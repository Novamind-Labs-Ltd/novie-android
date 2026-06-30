package com.novamind.app.common.google

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.novamind.app.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 日历授权来源抽象：供 ViewModel 依赖，便于注入假实现做单元测试，不泄漏 [Context]。
 *
 * - [requestAuthorization] 不弹 UI：已授权直接返回 token，否则返回待启动的同意意图，
 *   是否真正拉起同意由调用方决定（用于"静默续期"）。
 * - [revoke] 吊销当前授权（换账号场景），调 Google revoke 端点。
 */
interface GoogleCalendarAuthSource {
    suspend fun requestAuthorization(): GoogleCalendarAuthManager.Outcome
    suspend fun revoke(token: String)
}

/**
 * Google Calendar 授权封装层（设备端直连方案）。
 *
 * 用 Play Services 的 [com.google.android.gms.auth.api.identity.AuthorizationClient] 申请
 * `calendar.readonly` scope，拿到可直接调用 Google Calendar REST API 的 OAuth access token：
 * - 若用户此前已授权 → 直接返回 [Outcome.Authorized]（含 token）。
 * - 若需要用户选择账号 / 同意授权 → 返回 [Outcome.NeedsConsent]（含 [android.content.IntentSender]），
 *   由 UI 层用 ActivityResult 启动，回到前台后再调 [tokenFromConsentResult] 取 token。
 *
 * 前置条件：需在 Google Cloud 控制台为本应用配置 OAuth 同意屏幕，并创建与包名 + 签名 SHA-1
 * 对应的 **Android OAuth client**；Calendar API 需在该项目启用。否则授权会失败。
 */
class GoogleCalendarAuthManager(context: Context) : GoogleCalendarAuthSource {

    private val authorizationClient = Identity.getAuthorizationClient(context.applicationContext)

    private val requestedScopes = listOf(Scope(SCOPE_CALENDAR_READONLY))

    /** 仅用于吊销的小客户端，与 Calendar 业务网络栈无关。 */
    private val revokeClient: OkHttpClient by lazy { OkHttpClient() }

    sealed interface Outcome {
        /** 已授权，[accessToken] 可直接用于调用 Calendar API。 */
        data class Authorized(val accessToken: String) : Outcome

        /** 需要用户交互（选账号 / 同意授权），UI 层用此 [intentSender] 启动同意流程。 */
        data class NeedsConsent(val intentSender: android.content.IntentSender) : Outcome
    }

    /**
     * 申请 Calendar 只读授权。不弹任何 UI 时直接返回 token；需要用户交互时返回待启动的同意意图。
     * 失败（如未配置 OAuth client、无 Google 账号、网络异常）抛出异常，由调用方处理。
     */
    override suspend fun requestAuthorization(): Outcome = suspendCancellableCoroutine { cont ->
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(requestedScopes)
            .build()
        authorizationClient.authorize(request)
            .addOnSuccessListener { result ->
                cont.resume(result.toOutcome())
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
    }

    /**
     * 解析同意流程返回的 Intent，取出 access token。
     * 解析失败（如 OAuth client / SHA-1 不匹配）会抛出 [com.google.android.gms.common.api.ApiException]，
     * 由调用方捕获并展示真实原因，而不是统一当成「取消」。data 为 null 返回 null。
     */
    fun tokenFromConsentResult(data: Intent?): String? {
        if (data == null) return null
        return authorizationClient.getAuthorizationResultFromIntent(data)
            .accessToken
            ?.takeIf { it.isNotBlank() }
    }

    private fun AuthorizationResult.toOutcome(): Outcome {
        val pendingIntent = pendingIntent
        if (hasResolution() && pendingIntent != null) {
            return Outcome.NeedsConsent(pendingIntent.intentSender)
        }
        // 无需交互即返回时必须带 token；为空视为异常，避免把空 token 当成已授权（会导致 401）。
        val token = accessToken
        check(!token.isNullOrBlank()) { "Authorization succeeded but no access token returned" }
        return Outcome.Authorized(token)
    }

    /**
     * 吊销 [token] 对应的授权（换账号时调用）。吊销后系统不再记住该 grant，
     * 下次 [requestAuthorization] 会返回 [Outcome.NeedsConsent] 并重新弹出账号选择器。
     * 失败不抛出（best-effort），本地清理照常进行。
     */
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
        private const val REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"
        private const val TAG = "CalendarAuth"
    }
}
