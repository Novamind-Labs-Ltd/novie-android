package com.novamind.app.common.google

import com.novamind.app.common.log.AppLog
import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
 * 设计为「显式账号」模型：调用方传入**指定账号**（默认即当前 App 登录账户邮箱），
 * 用 [fetchToken] 为该账号取 token——不弹账号选择器，日历账户始终跟随登录账户。
 */
interface GoogleCalendarAuthSource {
    /** 为 [accountName] 取 calendar.readonly 的 access token。 */
    suspend fun fetchToken(accountName: String): TokenOutcome

    /** 清除 GMS 本地缓存的该 token（过期重取 / 换账号 / 登出前）。 */
    suspend fun clearToken(token: String)

    /** 服务端吊销该 token（换账号时，best-effort，失败不抛）。 */
    suspend fun revoke(token: String)

    /** 撤销指定账号授予本 App 的 Calendar / Tasks scopes。 */
    suspend fun revokeAccount(accountName: String)
}

/**
 * Google Calendar 授权封装层（设备端直连方案，账号跟随登录账户）。
 *
 * - [fetchToken]：用 [GoogleAuthUtil.getToken] 为指定账号取 `calendar.readonly` 的 OAuth
 *   access token。已授权直接返回；需要用户同意时抛 [UserRecoverableAuthException]，
 *   转成 [TokenOutcome.NeedsConsent] 由 UI 启动恢复意图，返回后重试。
 *
 * 前置条件：需在 Google Cloud 控制台配置 OAuth 同意屏幕，并创建与包名 + 签名 SHA-1
 * 对应的 OAuth client，且启用 Calendar API；登录账户须为设备上的 Google 账号，否则取 token 会失败。
 */
class GoogleCalendarAuthManager(context: Context) : GoogleCalendarAuthSource {

    private val appContext = context.applicationContext
    private val authorizationClient = Identity.getAuthorizationClient(appContext)
    private val requestedScopes = listOf(
        Scope(SCOPE_CALENDAR_READONLY),
        Scope(SCOPE_CALENDAR_EVENTS),
        Scope(SCOPE_TASKS),
    )

    /** 仅用于服务端吊销的小客户端，与 Calendar 业务网络栈无关。 */
    private val revokeClient: OkHttpClient by lazy { OkHttpClient() }

    /** 交互式账号选择/授权的结果。 */
    sealed interface AuthorizationOutcome {
        data class Authorized(val accessToken: String) : AuthorizationOutcome
        data class NeedsConsent(val intentSender: android.content.IntentSender) : AuthorizationOutcome
    }

    /**
     * 连接时使用 AuthorizationClient，并且始终让用户明确选择设备上的 Google 账号。
     *
     * AuthorizationClient 默认会复用该 App 上次已授权的 eligible account，可能直接返回
     * 旧账号 token 而不展示账号选择器。Novie 登录用户切换后，这会把新用户错误绑定到旧用户
     * 的 Calendar。SELECT_ACCOUNT 禁止这种静默复用，让每次建立本地绑定都由用户明确确认账号。
     * 不再假设 Novie/Auth0 登录邮箱也是 Android 系统 Google 账号。
     */
    suspend fun requestAuthorization(): AuthorizationOutcome = suspendCancellableCoroutine { continuation ->
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(requestedScopes)
            .setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
            .build()
        authorizationClient.authorize(request)
            .addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result.toAuthorizationOutcome())
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
    }

    /** 解析账号选择/授权页面返回的 access token。 */
    fun tokenFromAuthorizationResult(data: Intent?): String? {
        if (data == null) return null
        return authorizationClient.getAuthorizationResultFromIntent(data)
            .accessToken
            ?.takeIf { it.isNotBlank() }
    }

    private fun AuthorizationResult.toAuthorizationOutcome(): AuthorizationOutcome {
        val resolution = pendingIntent
        if (hasResolution() && resolution != null) {
            return AuthorizationOutcome.NeedsConsent(resolution.intentSender)
        }
        val token = accessToken
        check(!token.isNullOrBlank()) { "Authorization succeeded without an access token" }
        return AuthorizationOutcome.Authorized(token)
    }

    override suspend fun fetchToken(accountName: String): TokenOutcome = withContext(Dispatchers.IO) {
        try {
            val token = GoogleAuthUtil.getToken(
                appContext,
                Account(accountName, GOOGLE_ACCOUNT_TYPE),
                OAUTH2_SCOPE,
            )
            AppLog.d(TAG) { "fetchToken success for $accountName" }
            TokenOutcome.Success(token)
        } catch (e: UserRecoverableAuthException) {
            val intent = e.intent
            if (intent != null) {
                TokenOutcome.NeedsConsent(intent)
            } else {
                AppLog.w(TAG, e) { "getToken recoverable but no intent for $accountName" }
                TokenOutcome.Failure(e)
            }
        } catch (e: GoogleAuthException) {
            // 不可恢复：多为 OAuth client(包名/SHA-1) / 同意屏幕 / scope 未配，
            // 或该账号是受管控的 Workspace 账号、未加入测试用户。
            AppLog.e(TAG, e) { "getToken non-recoverable for $accountName scope=$OAUTH2_SCOPE: " +
                    "${e.javaClass.simpleName} ${e.message}" }
            TokenOutcome.Failure(e)
        } catch (e: IOException) {
            AppLog.w(TAG, e) { "getToken network error for $accountName" }
            TokenOutcome.Failure(e)
        } catch (e: Exception) {
            AppLog.w(TAG, e) { "getToken failed for $accountName: ${e.javaClass.simpleName}" }
            TokenOutcome.Failure(e)
        }
    }

    override suspend fun clearToken(token: String): Unit = withContext(Dispatchers.IO) {
        runCatching { GoogleAuthUtil.clearToken(appContext, token) }
            .onFailure { AppLog.w(TAG, it) { "clearToken failed" } }
        Unit
    }

    override suspend fun revoke(token: String): Unit = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$REVOKE_ENDPOINT?token=$token")
                .post(ByteArray(0).toRequestBody(null))
                .build()
            revokeClient.newCall(request).execute().use { resp ->
                AppLog.d(TAG) { "revoke token http=${resp.code}" }
            }
        }.onFailure { AppLog.w(TAG, it) { "revoke token failed" } }
        Unit
    }

    override suspend fun revokeAccount(accountName: String): Unit =
        suspendCancellableCoroutine { continuation ->
            val request = RevokeAccessRequest.builder()
                .setAccount(Account(accountName, GOOGLE_ACCOUNT_TYPE))
                .setScopes(requestedScopes)
                .build()
            authorizationClient.revokeAccess(request)
                .addOnSuccessListener {
                    AppLog.d(TAG) { "revokeAccount success for $accountName" }
                    if (continuation.isActive) continuation.resume(Unit)
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }

    companion object {
        const val SCOPE_CALENDAR_READONLY = "https://www.googleapis.com/auth/calendar.readonly"
        // 事件读写 scope：编辑会议需要写权限（PATCH events）。calendar.events 覆盖事件读写；
        // 保留 calendar.readonly 以读取日历元数据（如 calendars/primary 取邮箱）。
        const val SCOPE_CALENDAR_EVENTS = "https://www.googleapis.com/auth/calendar.events"
        // 读写 scope：右滑完成任务需要写权限（PATCH task.status）。
        const val SCOPE_TASKS = "https://www.googleapis.com/auth/tasks"
        // 多个 scope 用空格分隔；scope 变化（新增 calendar.events）会触发已有用户重新同意一次。
        private const val OAUTH2_SCOPE =
            "oauth2:$SCOPE_CALENDAR_READONLY $SCOPE_CALENDAR_EVENTS $SCOPE_TASKS"
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"
        private const val TAG = "CalendarAuth"
    }
}
