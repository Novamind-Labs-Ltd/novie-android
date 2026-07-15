package com.novamind.app.feature.auth

import android.app.Activity
import android.app.Application
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.auth0.android.result.Credentials
import com.novamind.app.NovieApplication
import com.novamind.app.common.log.AppLog
import com.novamind.app.common.session.AuthSessionSignal
import com.novamind.app.common.session.UserSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 认证 ViewModel：持有单一 [AuthUiState]，对外暴露登录/登出/检查会话。
 * 登录登出需要 Activity（系统浏览器从 Activity 拉起），以参数瞬时传入，不持有引用。
 */
class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val authManager = AuthManager(app)
    private val biometricPrefs = BiometricPreferences()

    private val _uiState = MutableStateFlow(
        AuthUiState(
            biometricAvailable = authManager.isBiometricAvailable(),
            biometricEnabled = biometricPrefs.enabled,
        ),
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkSession()
        // 底层探测到会话失效（续期失败 / /me 40101）→ 强制登出到登录页。
        viewModelScope.launch {
            AuthSessionSignal.forceLogout.collect { forceLogout() }
        }
    }

    /**
     * 被动强制登出：由 [AuthSessionSignal] 触发（token 续期失败 / 服务端鉴权失效）。
     * 已在未登录 / 游客态则忽略，避免重复清理与门控抖动。
     */
    private fun forceLogout() {
        val state = _uiState.value
        if (!state.isAuthenticated || state.isGuest) return
        AppLog.w(TAG) { "session expired -> force logout" }
        authManager.logoutLocal()
        clearCalendarSession()
        _uiState.update { loggedOutState() }
    }

    /**
     * 启动时检查会话：
     * - 无凭证 → 未登录，显示登录页。
     * - 有凭证且开启指纹且设备可用 → 进入「待指纹解锁」状态（不直接放行）。
     * - 有凭证但未开启/不可用指纹 → 静默续期直接登录（沿用原行为）。
     */
    private fun checkSession() {
        val biometricGate = biometricPrefs.enabled && authManager.isBiometricAvailable()
        viewModelScope.launch {
            if (!authManager.hasValidCredentials()) {
                UserSessionManager.onLoggedOut()
                _uiState.update { it.copy(isCheckingSession = false, isAuthenticated = false, needsBiometricUnlock = false) }
                return@launch
            }
            if (biometricGate) {
                // 等待用户指纹解锁（由 UI 调用 unlockWithBiometric 触发系统指纹框）。
                _uiState.update {
                    it.copy(isCheckingSession = false, isAuthenticated = false, needsBiometricUnlock = true)
                }
                return@launch
            }
            // 无需指纹门控：静默取凭证直接登录。
            runCatching { authManager.getCredentials() }
                .onSuccess { creds ->
                    _uiState.update {
                        it.copy(
                            isCheckingSession = false,
                            isAuthenticated = true,
                            isGuest = false,
                            needsBiometricUnlock = false,
                        )
                    }
                    onSessionAuthenticated(creds.emailOrNull())
                }
                .onFailure {
                    _uiState.update { it.copy(isCheckingSession = false, isAuthenticated = false) }
                }
        }
    }

    /** 指纹解锁：弹系统生物识别框，通过后用本地凭证登入。失败保持待解锁态。 */
    fun unlockWithBiometric(activity: FragmentActivity) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { authManager.getCredentials(activity, requireBiometric = true) }
                .onSuccess { creds ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isAuthenticated = true,
                            isGuest = false,
                            needsBiometricUnlock = false,
                        )
                    }
                    onSessionAuthenticated(creds.emailOrNull())
                }
                .onFailure { e ->
                    // 用户取消或验证失败：留在解锁页，可重试或改用账号登录。
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Biometric verification failed") }
                }
        }
    }

    /** 放弃指纹解锁，改用账号登录：清除待解锁态，门控转到登录页。 */
    fun cancelBiometricUnlock() {
        _uiState.update {
            it.copy(needsBiometricUnlock = false, isAuthenticated = false, errorMessage = null)
        }
    }

    /** 设置「指纹登录」开关（持久化）。仅在已登录、设备支持时由 UI 调用。 */
    fun setBiometricEnabled(enabled: Boolean) {
        biometricPrefs.enabled = enabled
        _uiState.update { it.copy(biometricEnabled = enabled) }
    }

    // 进入后台的时刻（单调时钟，不受系统时间调整影响）。0 表示当前不在后台。
    private var backgroundedAt: Long = 0L

    /** App 整体进入后台：记录时刻，供回前台判断是否超时上锁。 */
    fun onAppBackgrounded() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    /**
     * App 回到前台：若已真实登录、开启了生物识别、且后台停留超过
     * [BACKGROUND_LOCK_TIMEOUT_MS]，则重新上锁（转到指纹/人脸解锁页）。
     */
    fun onAppForegrounded() {
        val enteredBackgroundAt = backgroundedAt
        backgroundedAt = 0L
        if (enteredBackgroundAt == 0L) return // 冷启动首次前台，无需处理

        val state = _uiState.value
        if (!state.isAuthenticated || state.isGuest) return

        // 回前台先静默续期一次（无生物识别提示），保持内存 access token 新鲜，
        // 再节流刷新档案：登录态下距上次成功刷新 ≥ 阈值才实际拉 /me（节流在 manager 内判定）。
        viewModelScope.launch {
            runCatching { authManager.getCredentials() }
            UserSessionManager.refreshFromServer(force = false)
        }

        if (!(biometricPrefs.enabled && authManager.isBiometricAvailable())) return

        val elapsed = SystemClock.elapsedRealtime() - enteredBackgroundAt
        if (elapsed >= BACKGROUND_LOCK_TIMEOUT_MS) {
            _uiState.update {
                it.copy(isAuthenticated = false, needsBiometricUnlock = true, errorMessage = null)
            }
        }
    }

    fun login(activity: Activity) {
        if (_uiState.value.isLoading) return
        // 临时口子：不走 Auth0，点「登录/注册」直接进入应用
        if (DEV_BYPASS_AUTH) {
            _uiState.update { it.copy(isLoading = false, isAuthenticated = true, errorMessage = null) }
            onSessionAuthenticated(email = null)
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { authManager.login(activity) }
                .onSuccess { creds ->
                    val email = creds.emailOrNull()
                    // 账户切换检测：本次登录用户与上次不同 → 清掉旧用户的日历绑定/缓存。
                    if (UserSessionManager.current.userKey != email) {
                        clearCalendarSession()
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isAuthenticated = true,
                            isGuest = false,
                            needsBiometricUnlock = false,
                        )
                    }
                    onSessionAuthenticated(email)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Sign-in failed") }
                }
        }
    }

    /** 免登录（游客模式）：不经过 Auth0，直接进入应用。游客不可用日历。 */
    fun loginAsGuest() {
        if (_uiState.value.isLoading) return
        // 先清掉可能残留的日历会话，再标记为游客（日历页将拦截显示「登录后使用」）。
        (getApplication() as NovieApplication).clearCalendarLocalSession()
        UserSessionManager.onGuest()
        _uiState.update {
            it.copy(
                isLoading = false,
                isAuthenticated = true,
                isGuest = true,
                errorMessage = null,
            )
        }
    }

    /**
     * 退出登录：仅清本地凭证、不打开浏览器，因而不会弹出浏览器「打开 App」确认框。
     * 如需连同 Auth0 的 SSO 会话一起清除（彻底登出），改用 [logoutFederated]。
     * 保留 activity 形参以兼容调用方。
     */
    fun logout(activity: Activity) {
        if (_uiState.value.isLoading) return
        authManager.logoutLocal()
        clearCalendarSession()
        _uiState.update { loggedOutState() }
    }

    /** 彻底登出：打开浏览器清空 Auth0 SSO 会话（会出现浏览器跳转 / 「打开 App」弹窗）。 */
    fun logoutFederated(activity: Activity) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { authManager.logout(activity) }
                .onSuccess {
                    clearCalendarSession()
                    _uiState.update {
                        AuthUiState(isCheckingSession = false, isAuthenticated = false)
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Sign-out failed") }
                }
        }
    }

    /** 游客切换到登录：重置为未登录状态，宿主门控会显示登录页。 */
    fun exitGuest() {
        clearCalendarSession()
        _uiState.update { loggedOutState() }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 登录 / 续期成功统一驱动全局会话：记录登录账户 [email] 并迁 AUTHENTICATED，
     * 随后强制拉一次 /me 校正档案（registered/profile）。
     */
    private fun onSessionAuthenticated(email: String?) {
        UserSessionManager.onLoggedIn(email)
        viewModelScope.launch { UserSessionManager.refreshFromServer(force = true) }
    }

    /**
     * 清除日历本地会话并清空全局用户会话。所有登出 / 退出游客路径调用：
     * 退出登录 → 清日历；不 revoke Google grant（重新登录同账号可静默恢复）。
     */
    private fun clearCalendarSession() {
        // 全局会话同步登出：清缓存 + MMKV 并迁未登录（账户切换时亦清旧号缓存，随后由登录流重建）。
        UserSessionManager.onLoggedOut()
        (getApplication() as NovieApplication).clearCalendarLocalSession()
    }

    /** 未登录初始态，保留生物识别能力/开关标记供 UI 一致显示。 */
    private fun loggedOutState() = AuthUiState(
        isCheckingSession = false,
        isAuthenticated = false,
        biometricAvailable = authManager.isBiometricAvailable(),
        biometricEnabled = biometricPrefs.enabled,
    )

    companion object {
        /**
         * 临时口子（开发用）。为 true 时登录/登出不走 Auth0，
         * 点「登录/注册」直接进入应用，方便未配置 Auth0 参数也能开发。
         * 已接入真实 Auth0：置为 false，登录走 Universal Login（PKCE）。
         */
        const val DEV_BYPASS_AUTH = false

        /** 后台停留超过此时长（毫秒）再回前台，要求重新生物识别。默认 5 分钟。 */
        private const val BACKGROUND_LOCK_TIMEOUT_MS = 5 * 60 * 1000L

        private const val TAG = "Auth"
    }
}

// 从 id_token（JWT）中解析登录邮箱，缺失时安全降级为 null。
// 直接解 JWT payload，避免依赖特定 SDK 版本的 Credentials.user。
private fun Credentials.emailOrNull(): String? = idTokenClaim("email")

private fun Credentials.idTokenClaim(key: String): String? = runCatching {
    val parts = idToken.split(".")
    if (parts.size < 2) return null
    val json = String(
        android.util.Base64.decode(
            parts[1],
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
        ),
        Charsets.UTF_8,
    )
    org.json.JSONObject(json).optString(key).takeIf { it.isNotBlank() }
}.getOrNull()
