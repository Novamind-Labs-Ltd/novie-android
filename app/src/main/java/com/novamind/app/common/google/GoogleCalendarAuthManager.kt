package com.novamind.app.common.google

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
class GoogleCalendarAuthManager(context: Context) {

    private val authorizationClient = Identity.getAuthorizationClient(context.applicationContext)

    private val requestedScopes = listOf(Scope(SCOPE_CALENDAR_READONLY))

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
    suspend fun requestAuthorization(): Outcome = suspendCancellableCoroutine { cont ->
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

    companion object {
        const val SCOPE_CALENDAR_READONLY = "https://www.googleapis.com/auth/calendar.readonly"
    }
}
