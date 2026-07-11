package com.novamind.app.feature.auth

import android.app.Activity
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationAPIClient
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.authentication.storage.AuthenticationLevel
import com.auth0.android.authentication.storage.CredentialsManagerException
import com.auth0.android.authentication.storage.LocalAuthenticationOptions
import com.auth0.android.authentication.storage.SecureCredentialsManager
import com.auth0.android.authentication.storage.SharedPreferencesStorage
import com.auth0.android.callback.Callback
import com.auth0.android.provider.BrowserPicker
import com.auth0.android.provider.CustomTabsOptions
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials
import com.novamind.app.BuildConfig
import com.novamind.app.common.net.TokenProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Auth0 认证封装层。集中处理：
 * - Universal Login 登录 / 登出（[WebAuthProvider]，走系统浏览器，PKCE）
 * - 凭证持久化与自动续期（[SecureCredentialsManager] + SharedPreferences）
 * - 指纹/生物识别门控取凭证（借助 Android 原生 BiometricPrompt）
 *
 * 凭证用 [SecureCredentialsManager] 加密落盘（Keystore + RSA/AES）。取凭证时
 * 是否要求指纹，取决于构造时是否传入 FragmentActivity + [LocalAuthenticationOptions]：
 * - 传入 → [getCredentials] 会先弹生物识别框，通过才返回凭证；
 * - 不传 → 静默返回。两种方式共用同一加密存储（KEY_ALIAS 固定），可自由切换。
 *
 * 配置（Client ID / Domain / Scheme / Audience）来自 auth0.properties → BuildConfig。
 */
class AuthManager(context: Context) {

    private val appContext = context.applicationContext

    private val account: Auth0 = Auth0.getInstance(
        BuildConfig.AUTH0_CLIENT_ID,
        BuildConfig.AUTH0_DOMAIN,
    )

    private val storage = SharedPreferencesStorage(appContext)

    /** 静默凭证管理器：保存/清除/检查/无指纹取凭证。 */
    private val baseManager = SecureCredentialsManager(appContext, account, storage)

    /** 本地是否已有未过期（或可凭 refresh_token 续期）的凭证，无网络请求。 */
    fun hasValidCredentials(): Boolean = baseManager.hasValidCredentials()

    /**
     * 设备是否可用生物识别（已录入指纹/人脸等，Class 2 或以上）。
     * 决定「指纹登录」开关是否对用户显示、以及启动时是否走指纹门控。
     */
    fun isBiometricAvailable(): Boolean {
        val manager = BiometricManager.from(appContext)
        val strong = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
        val weak = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
        return strong || weak
    }

    /** 发起 Universal Login。成功后凭证已落盘。失败抛 [AuthenticationException]。 */
    suspend fun login(activity: Activity): Credentials =
        suspendCancellableCoroutine { cont ->
            val builder = WebAuthProvider.login(account)
                .withScheme(BuildConfig.AUTH0_SCHEME)
                .withScope("openid profile email offline_access")
                // 优先 Chrome，缺失则降级系统默认浏览器（详见 buildCustomTabsOptions）。
                .withCustomTabsOptions(buildCustomTabsOptions())
                // 强制每次都展示登录页，忽略已有 SSO 会话，
                // 从而允许用户在重新登录时切换账户。
                // 如需账户选择器可改为 "select_account"（取决于上游 IdP 支持）。
                .withParameters(mapOf("prompt" to "login"))
            if (BuildConfig.AUTH0_AUDIENCE.isNotBlank()) {
                builder.withAudience(BuildConfig.AUTH0_AUDIENCE)
            }
            builder.start(activity, object : Callback<Credentials, AuthenticationException> {
                override fun onSuccess(result: Credentials) {
                    baseManager.saveCredentials(result)
                    TokenProvider.accessToken = result.accessToken
                    if (cont.isActive) cont.resume(result)
                }

                override fun onFailure(error: AuthenticationException) {
                    if (cont.isActive) cont.resumeWithException(error)
                }
            })
        }

    /**
     * 仅本地登出：只清除本地凭证，不打开浏览器清 SSO cookie。
     * 因此不会触发浏览器「打开 App」确认弹窗；代价是 Auth0 的浏览器会话仍在，
     * 下次登录可能因 SSO 直接登入（不再要求输入密码）。
     */
    fun logoutLocal() {
        baseManager.clearCredentials()
        TokenProvider.accessToken = null
    }

    /** 完整登出：打开浏览器清空 SSO cookie 与本地凭证。失败抛 [AuthenticationException]。 */
    suspend fun logout(activity: Activity) =
        suspendCancellableCoroutine { cont ->
            WebAuthProvider.logout(account)
                .withScheme(BuildConfig.AUTH0_SCHEME)
                .withCustomTabsOptions(buildCustomTabsOptions())
                .start(activity, object : Callback<Void?, AuthenticationException> {
                    override fun onSuccess(result: Void?) {
                        baseManager.clearCredentials()
                        TokenProvider.accessToken = null
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onFailure(error: AuthenticationException) {
                        if (cont.isActive) cont.resumeWithException(error)
                    }
                })
        }

    /**
     * 取有效凭证，过期会用 refresh_token 自动续期并保存。失败抛 [CredentialsManagerException]。
     *
     * @param activity 非空且 [requireBiometric] 为 true 时，取凭证前会弹生物识别框（需 FragmentActivity）。
     * @param requireBiometric 是否要求指纹/生物识别门控。
     */
    suspend fun getCredentials(
        activity: FragmentActivity? = null,
        requireBiometric: Boolean = false,
    ): Credentials =
        suspendCancellableCoroutine { cont ->
            val manager = if (requireBiometric && activity != null) {
                SecureCredentialsManager(
                    appContext,
                    account,
                    storage,
                    activity,
                    buildLocalAuthOptions(),
                )
            } else {
                baseManager
            }
            manager.getCredentials(object :
                Callback<Credentials, CredentialsManagerException> {
                override fun onSuccess(result: Credentials) {
                    TokenProvider.accessToken = result.accessToken
                    if (cont.isActive) cont.resume(result)
                }

                override fun onFailure(error: CredentialsManagerException) {
                    if (cont.isActive) cont.resumeWithException(error)
                }
            })
        }

    /**
     * 生物识别提示框配置。
     *
     * 用 [AuthenticationLevel.WEAK]（Class 2）：请求 WEAK 时系统会接受「不低于
     * Class 2」的全部生物特征——既包含多数机型的人脸（通常被归为 Class 2），
     * 也包含指纹（Class 3 同样满足）。这样指纹与人脸都能用，代价是安全门槛降到
     * Class 2。如需仅强生物识别，把级别改回 STRONG（但多数机型人脸将不可用）。
     *
     * 允许回退到设备 PIN/图案/密码。WEAK + 设备凭证回退在各 API 上均受支持
     * （受限的只是 STRONG+回退于 API 28/29、以及 DEVICE_CREDENTIAL 单独用于 ≤29）。
     */
    private fun buildLocalAuthOptions(): LocalAuthenticationOptions =
        LocalAuthenticationOptions.Builder()
            .setTitle("Fingerprint / Face sign-in")
            .setDescription("Verify your identity to continue")
            .setAuthenticationLevel(AuthenticationLevel.WEAK)
            .setDeviceCredentialFallback(true)
            .setNegativeButtonText("Cancel")
            .build()

    /**
     * 构造 Custom Tabs 选项：优先用 Chrome，缺失或被禁用时降级到系统默认浏览器。
     *
     * 注意不能用 [BrowserPicker.withAllowedPackages] 硬白名单只放 Chrome——那样
     * 没装 Chrome 会直接打不开登录页。因此先运行时检测 Chrome 是否可用：
     * 可用才限定到 Chrome，否则返回不限制的默认选项（Auth0 自行挑选支持
     * Custom Tabs 的默认浏览器）。
     */
    private fun buildCustomTabsOptions(): CustomTabsOptions {
        val builder = CustomTabsOptions.newBuilder()
        if (isChromeUsable()) {
            builder.withBrowserPicker(
                BrowserPicker.newBuilder()
                    .withAllowedPackages(listOf(CHROME_PACKAGE))
                    .build(),
            )
        }
        return builder.build()
    }

    /** Chrome 正式版是否已安装且未被禁用。 */
    private fun isChromeUsable(): Boolean = runCatching {
        appContext.packageManager.getApplicationInfo(CHROME_PACKAGE, 0).enabled
    }.getOrDefault(false)

    private companion object {
        const val CHROME_PACKAGE = "com.android.chrome"
    }
}
