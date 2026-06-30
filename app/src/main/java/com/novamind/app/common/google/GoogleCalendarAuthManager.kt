package com.novamind.app.common.google

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.common.AccountPicker
import com.novamind.app.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 为某个 Google 账号取 token 的结果。 */
sealed interface TokenOutcome {
    /** 成功拿到 access token。 */
    data class Success(val token: String) : TokenOutcome

    /** 需要用户同意 / 恢复授权。UI 用 [recoveryIntent] 启动，返回后重试取 token。 */
    data class NeedsConsent(val recoveryIntent: Intent) : TokenOutcome

    /** 失败（无网络、配置错误等）。 */
    data class Failure(val error: Throwable) : TokenOutcome
}

/**
 * 日历授权来源抽象：供 ViewModel 依赖，便于注入假实现做单元测试，不泄漏 [Context]。
 *
 * 设计为「显式账号」模型：先由用户从 [newAccountChooserIntent] 选定 Google 账号，
 * 再用 [fetchToken] 为**该账号**取 token——从而支持确定性的「换账号」（选哪个就用哪个），
 * 解决 Identity AuthorizationClient 静默复用旧账号、无法切换的问题。
 */
interface GoogleCalendarAuthSource {
    /** 账号选择器 Intent，列出设备上的 Google 账号供用户选择。 */
    fun newAccountChooserIntent(): Intent

    /** 为 [accountName] 取 calendar.readonly 的 access token。 */
    suspend fun fetchToken(accountName: String): TokenOutcome

    /** 清除 GMS 本地缓存的该 token（过期重取 / 换账号 / 登出前）。 */
    suspend fun clearToken(token: String)

    /** 服务端吊销该 token（换账号时，best-effort，失败不抛）。 */
    suspend fun revoke(token: String)
}

/**
 * Google Calendar 授权封装层（设备端直连方案，显式账号）。
 *
 * - [newAccountChooserIntent]：用 [AccountPicker] 弹出 Google 账号选择器。
 * - [fetchToken]：用 [GoogleAuthUtil.getToken] 为所选账号取 `calendar.readonly` 的 OAuth
 *   access token。已授权直接返回；需要用户同意时抛 [UserRecoverableAuthException]，
 *   转成 [TokenOutcome.NeedsConsent] 由 UI 启动恢复意图，返回后重试。
 *
 * 前置条件：需在 Google Cloud 控制台配置 OAuth 同意屏幕，并创建与包名 + 签名 SHA-1
 * 对应的 OAuth client，且启用 Calendar API；否则取 token 会失败。
 */
class GoogleCalendarAuthManager(context: Context) : GoogleCalendarAuthSource {

    private val appContext = context.applicationContext

    /** 仅用于服务端吊销的小客户端，与 Calendar 业务网络栈无关。 */
    private val revokeClient: OkHttpClient by lazy { OkHttpClient() }

    override fun newAccountChooserIntent(): Intent {
        val options = AccountPicker.AccountChooserOptions.Builder()
            .setAllowableAccountsTypes(listOf(GOOGLE_ACCOUNT_TYPE))
            .build()
        return AccountPicker.newChooseAccountIntent(options)
    }

    override suspend fun fetchToken(accountName: String): TokenOutcome = withContext(Dispatchers.IO) {
        try {
            val token = GoogleAuthUtil.getToken(
                appContext,
                Account(accountName, GOOGLE_ACCOUNT_TYPE),
                OAUTH2_SCOPE,
            )
            LogUtils.d("fetchToken success for $accountName", TAG)
            TokenOutcome.Success(token)
        } catch (e: UserRecoverableAuthException) {
            val intent = e.intent
            if (intent != null) {
                TokenOutcome.NeedsConsent(intent)
            } else {
                LogUtils.w("getToken recoverable but no intent", e, TAG)
                TokenOutcome.Failure(e)
            }
        } catch (e: Exception) {
            LogUtils.w("getToken failed for $accountName", e, TAG)
            TokenOutcome.Failure(e)
        }
    }

    override suspend fun clearToken(token: String): Unit = withContext(Dispatchers.IO) {
        runCatching { GoogleAuthUtil.clearToken(appContext, token) }
            .onFailure { LogUtils.w("clearToken failed", it, TAG) }
        Unit
    }

    override suspend fun revoke(token: String): Unit = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$REVOKE_ENDPOINT?token=$token")
                .post(ByteArray(0).toRequestBody(null))
                .build()
            revokeClient.newCall(request).execute().use { resp ->
                LogUtils.d("revoke token http=${resp.code}", TAG)
            }
        }.onFailure { LogUtils.w("revoke token failed", it, TAG) }
        Unit
    }

    companion object {
        const val SCOPE_CALENDAR_READONLY = "https://www.googleapis.com/auth/calendar.readonly"
        private const val OAUTH2_SCOPE = "oauth2:$SCOPE_CALENDAR_READONLY"
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"
        private const val TAG = "CalendarAuth"
    }
}
