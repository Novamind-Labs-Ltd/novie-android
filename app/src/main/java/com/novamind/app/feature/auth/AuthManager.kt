package com.novamind.app.feature.auth

import android.app.Activity
import android.content.Context
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationAPIClient
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.authentication.storage.CredentialsManager
import com.auth0.android.authentication.storage.CredentialsManagerException
import com.auth0.android.authentication.storage.SharedPreferencesStorage
import com.auth0.android.callback.Callback
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials
import com.novamind.app.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Auth0 认证封装层。集中处理：
 * - Universal Login 登录 / 登出（[WebAuthProvider]，走系统浏览器，PKCE）
 * - 凭证持久化与自动续期（[CredentialsManager] + SharedPreferences）
 *
 * 配置（Client ID / Domain / Scheme / Audience）来自 auth0.properties → BuildConfig。
 * 这里统一使用稳定的回调式 API，并用协程包装，避免依赖具体扩展函数。
 */
class AuthManager(context: Context) {

    private val account: Auth0 = Auth0.getInstance(
        BuildConfig.AUTH0_CLIENT_ID,
        BuildConfig.AUTH0_DOMAIN,
    )

    private val authClient = AuthenticationAPIClient(account)

    private val credentialsManager = CredentialsManager(
        authClient,
        SharedPreferencesStorage(context.applicationContext),
    )

    /** 本地是否已有未过期（或可凭 refresh_token 续期）的凭证，无网络请求。 */
    fun hasValidCredentials(): Boolean = credentialsManager.hasValidCredentials()

    /** 发起 Universal Login。成功后凭证已落盘。失败抛 [AuthenticationException]。 */
    suspend fun login(activity: Activity): Credentials =
        suspendCancellableCoroutine { cont ->
            val builder = WebAuthProvider.login(account)
                .withScheme(BuildConfig.AUTH0_SCHEME)
                .withScope("openid profile email offline_access")
            if (BuildConfig.AUTH0_AUDIENCE.isNotBlank()) {
                builder.withAudience(BuildConfig.AUTH0_AUDIENCE)
            }
            builder.start(activity, object : Callback<Credentials, AuthenticationException> {
                override fun onSuccess(result: Credentials) {
                    credentialsManager.saveCredentials(result)
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
        credentialsManager.clearCredentials()
    }

    /** 完整登出：打开浏览器清空 SSO cookie 与本地凭证。失败抛 [AuthenticationException]。 */
    suspend fun logout(activity: Activity) =
        suspendCancellableCoroutine { cont ->
            WebAuthProvider.logout(account)
                .withScheme(BuildConfig.AUTH0_SCHEME)
                .start(activity, object : Callback<Void?, AuthenticationException> {
                    override fun onSuccess(result: Void?) {
                        credentialsManager.clearCredentials()
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onFailure(error: AuthenticationException) {
                        if (cont.isActive) cont.resumeWithException(error)
                    }
                })
        }

    /** 取有效凭证，过期会用 refresh_token 自动续期并保存。失败抛 [CredentialsManagerException]。 */
    suspend fun getCredentials(): Credentials =
        suspendCancellableCoroutine { cont ->
            credentialsManager.getCredentials(object :
                Callback<Credentials, CredentialsManagerException> {
                override fun onSuccess(result: Credentials) {
                    if (cont.isActive) cont.resume(result)
                }

                override fun onFailure(error: CredentialsManagerException) {
                    if (cont.isActive) cont.resumeWithException(error)
                }
            })
        }
}
