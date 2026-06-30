package com.novamind.app.feature.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.NovieApplication
import com.novamind.app.common.google.GoogleAccount
import com.novamind.app.common.google.GoogleCalendarAuthManager
import com.novamind.app.common.google.GoogleCalendarAuthSource
import com.novamind.app.common.google.GoogleTokenProvider
import com.novamind.app.common.session.AppUserProvider
import com.novamind.app.data.calendar.CalendarEventCache
import com.novamind.app.data.calendar.GoogleAuthExpiredException
import com.novamind.app.data.calendar.GoogleAuthRevokedException
import com.novamind.app.data.calendar.GoogleCalendarRepository
import com.novamind.app.util.LogUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Calendar 页面 ViewModel：以 [CalendarConnectionStatus] 状态机驱动 UI。
 *
 * 设计见 calendar-connection-design.md。职责：
 * - 进入页面尝试**静默续期**（已授权则无感刷新，不弹 UI）；
 * - 按日期拉取事件，先渲染按账号隔离的缓存再后台刷新；
 * - 编排断开 / 换账号 / 重试，并把鉴权错误分流到对应状态。
 *
 * 需 Activity 的交互式授权（选账号/同意）仍在 [CalendarRoute]；本类只用
 * [GoogleCalendarAuthSource] 做静默授权与 revoke。
 */
class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NovieApplication
    private val repository: GoogleCalendarRepository = app.googleCalendarRepository
    private val bindingStore: CalendarBindingStore = app.calendarBindingStore
    private val eventCache: CalendarEventCache = app.calendarEventCache
    private val authSource: GoogleCalendarAuthSource = app.googleCalendarAuthSource

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState = _uiState.asStateFlow()

    init {
        // 响应式联动 App 会话：登录/登出/游客切换时重新评估，避免 VM 被保留后状态停滞
        // （如游客进过日历页后登录，仍显示「登录后使用」）。StateFlow 会立即发射当前值。
        viewModelScope.launch {
            AppUserProvider.session.collect { refreshAuthAndLoad() }
        }
    }

    fun onEvent(event: CalendarUiEvent) {
        when (event) {
            // Connect / SwitchAccount 需 Activity，由 Route 拦截编排，VM 不处理。
            CalendarUiEvent.Connect -> Unit
            CalendarUiEvent.SwitchAccount -> Unit
            is CalendarUiEvent.GoogleTokenObtained -> onTokenObtained(event.accessToken)
            is CalendarUiEvent.AuthFailed -> _uiState.update {
                it.copy(errorMessage = event.message ?: "Google authorization failed")
            }
            CalendarUiEvent.Disconnect -> onDisconnect()
            CalendarUiEvent.Retry -> loadEvents()
            is CalendarUiEvent.DateSelected -> selectDate(event.date)
            CalendarUiEvent.PrevDay -> selectDate(_uiState.value.selectedDate.minusDays(1))
            CalendarUiEvent.NextDay -> selectDate(_uiState.value.selectedDate.plusDays(1))
            CalendarUiEvent.ToggleMorning -> _uiState.update { it.copy(morningExpanded = !it.morningExpanded) }
            CalendarUiEvent.ToggleAfternoon -> _uiState.update { it.copy(afternoonExpanded = !it.afternoonExpanded) }
            CalendarUiEvent.Refresh -> if (_uiState.value.isConnected) loadEvents()
            CalendarUiEvent.ErrorShown -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    /**
     * 进入页面 / 冷启动的入口。有绑定记录则先渲染缓存并尝试静默续期：
     * - 静默拿到 token → 同步；
     * - 需要重新同意（grant 已失效）→ [CalendarConnectionStatus.PERMISSION_REVOKED]。
     * 无绑定记录 → [CalendarConnectionStatus.NOT_CONNECTED]，显示首次引导。
     */
    private fun refreshAuthAndLoad() {
        // 游客（免登录）不可用日历：拦截为「登录后使用」，不触发任何授权/拉取。
        if (AppUserProvider.isGuest) {
            LogUtils.d("refreshAuthAndLoad: guest -> LOGIN_REQUIRED", TAG)
            _uiState.update { CalendarUiState(connectionStatus = CalendarConnectionStatus.LOGIN_REQUIRED) }
            return
        }
        if (!bindingStore.isConnected) {
            LogUtils.d("refreshAuthAndLoad: no binding -> NOT_CONNECTED", TAG)
            _uiState.update { it.copy(connectionStatus = CalendarConnectionStatus.NOT_CONNECTED) }
            return
        }
        // 软一致性：绑定时的 App 用户 ≠ 当前登录用户（如已切换账号）→ 清日历，回未连接。
        val currentUser = AppUserProvider.currentUserKey
        if (bindingStore.appUserKey != currentUser) {
            LogUtils.d("refreshAuthAndLoad: app user mismatch -> clear calendar", TAG)
            clearLocalSession()
            _uiState.update { CalendarUiState(selectedDate = it.selectedDate) }
            return
        }
        val email = bindingStore.accountEmail
        viewModelScope.launch {
            val date = _uiState.value.selectedDate
            val cached = email?.let { eventCache.get(it, date) }
            _uiState.update {
                it.copy(
                    connectionStatus = CalendarConnectionStatus.SYNCING,
                    account = email?.let { e -> GoogleAccount(e) } ?: it.account,
                    events = cached ?: it.events,
                    errorMessage = null,
                )
            }
            if (trySilentAuthorize()) {
                fetchInto(date, email, allowSilentRetry = false)
            } else {
                LogUtils.d("refreshAuthAndLoad: silent authorize needs consent -> REVOKED", TAG)
                GoogleTokenProvider.clear()
                _uiState.update {
                    it.copy(
                        connectionStatus = CalendarConnectionStatus.PERMISSION_REVOKED,
                        errorMessage = "Google authorization expired, please reconnect",
                    )
                }
            }
        }
    }

    /** 交互式授权成功后：写 token、解析账号、写绑定、拉取事件。 */
    private fun onTokenObtained(token: String) {
        GoogleTokenProvider.accessToken = token
        viewModelScope.launch {
            _uiState.update { it.copy(connectionStatus = CalendarConnectionStatus.SYNCING, errorMessage = null) }
            val email = runCatching { repository.currentAccountEmail() }
                .onFailure { LogUtils.w("resolve account email failed", it, TAG) }
                .getOrNull()
            if (email != null) {
                bindingStore.bind(email, AppUserProvider.currentUserKey)
                _uiState.update { it.copy(account = GoogleAccount(email)) }
            }
            fetchInto(_uiState.value.selectedDate, email ?: _uiState.value.account?.email, allowSilentRetry = false)
        }
    }

    private fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        if (_uiState.value.isConnected) loadEvents()
    }

    /** 拉取选中日期事件：进入同步态、先渲染缓存，再请求网络。 */
    private fun loadEvents() {
        val date = _uiState.value.selectedDate
        val accountId = _uiState.value.account?.email
        viewModelScope.launch {
            LogUtils.d("loadEvents start: date=$date", TAG)
            val cached = accountId?.let { eventCache.get(it, date) }
            _uiState.update {
                it.copy(
                    connectionStatus = CalendarConnectionStatus.SYNCING,
                    events = cached ?: it.events,
                    errorMessage = null,
                )
            }
            fetchInto(date, accountId, allowSilentRetry = true)
        }
    }

    /**
     * 实际拉取并落地结果。401 时进入 [CalendarConnectionStatus.TOKEN_EXPIRED] 并静默续期一次；
     * 续期失败或 403 → [CalendarConnectionStatus.PERMISSION_REVOKED]；其余 → [CalendarConnectionStatus.SYNC_FAILED]。
     */
    private suspend fun fetchInto(date: LocalDate, accountId: String?, allowSilentRetry: Boolean) {
        runCatching { repository.eventsOn(date) }
            .onSuccess { events ->
                LogUtils.d("loadEvents success: date=$date, count=${events.size}", TAG)
                if (accountId != null) eventCache.put(accountId, date, events)
                _uiState.update {
                    it.copy(connectionStatus = CalendarConnectionStatus.CONNECTED, events = events)
                }
            }
            .onFailure { e ->
                LogUtils.e("loadEvents failure: date=$date", e, TAG)
                when (e) {
                    is GoogleAuthExpiredException -> {
                        _uiState.update { it.copy(connectionStatus = CalendarConnectionStatus.TOKEN_EXPIRED) }
                        if (allowSilentRetry && trySilentAuthorize()) {
                            fetchInto(date, accountId, allowSilentRetry = false)
                        } else {
                            GoogleTokenProvider.clear()
                            _uiState.update {
                                it.copy(
                                    connectionStatus = CalendarConnectionStatus.PERMISSION_REVOKED,
                                    errorMessage = "Google authorization expired, please reconnect",
                                )
                            }
                        }
                    }
                    is GoogleAuthRevokedException -> {
                        GoogleTokenProvider.clear()
                        _uiState.update {
                            it.copy(
                                connectionStatus = CalendarConnectionStatus.PERMISSION_REVOKED,
                                errorMessage = "Google authorization revoked, please reconnect",
                            )
                        }
                    }
                    else -> _uiState.update {
                        it.copy(
                            connectionStatus = CalendarConnectionStatus.SYNC_FAILED,
                            errorMessage = e.message ?: "Failed to load calendar",
                        )
                    }
                }
            }
    }

    /** 静默授权：拿到 token 返回 true（已写入 provider）；需要同意 / 失败返回 false。不弹 UI。 */
    private suspend fun trySilentAuthorize(): Boolean =
        runCatching { authSource.requestAuthorization() }
            .onFailure { LogUtils.w("silent authorize failed", it, TAG) }
            .getOrNull()
            ?.let { outcome ->
                when (outcome) {
                    is GoogleCalendarAuthManager.Outcome.Authorized -> {
                        GoogleTokenProvider.accessToken = outcome.accessToken
                        true
                    }
                    is GoogleCalendarAuthManager.Outcome.NeedsConsent -> false
                }
            } ?: false

    /** 断开 Calendar：删 token + 删绑定 + 清缓存（不 revoke），回到未连接。 */
    private fun onDisconnect() {
        LogUtils.d("disconnect calendar", TAG)
        clearLocalSession()
        _uiState.update { CalendarUiState(selectedDate = it.selectedDate) }
    }

    /**
     * 换账号前置清理：revoke 旧授权 + 删 token + 删绑定 + 清缓存。
     * Route 在此之后立即发起交互式授权（弹账号选择器）。
     */
    suspend fun prepareAccountSwitch() {
        LogUtils.d("prepare account switch (revoke + clear)", TAG)
        GoogleTokenProvider.accessToken?.let { authSource.revoke(it) }
        clearLocalSession()
        _uiState.update { CalendarUiState(selectedDate = it.selectedDate) }
    }

    /** 清本地会话（token + 绑定 + 缓存）。退出登录 / 断开 / 换账号共用。 */
    private fun clearLocalSession() {
        GoogleTokenProvider.clear()
        bindingStore.clear()
        eventCache.clear()
    }

    private companion object {
        const val TAG = "CalendarVM"
    }
}
