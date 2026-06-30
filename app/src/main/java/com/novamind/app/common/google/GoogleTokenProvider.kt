package com.novamind.app.common.google

/**
 * 内存态 Google OAuth access token 提供者。
 *
 * 与 [com.novamind.app.common.net.TokenProvider]（Auth0）独立：Google Calendar 走 googleapis.com，
 * 用的是 Google 授权得到的 access token，而非 App 自身的 Auth0 token。
 *
 * 授权层（[GoogleCalendarAuthManager] 申请/续期成功后）把 token 写到这里，
 * Google 专用的 OkHttp 拦截器同步读取并加到 Authorization 头。断开连接时置空。
 *
 * 说明：Google access token 默认约 1 小时过期，过期后请求会返回 401，
 * 由上层捕获并提示用户重新授权（静默续期可在后续迭代用 AuthorizationClient 再次申请实现）。
 */
object GoogleTokenProvider {
    @Volatile
    var accessToken: String? = null

    /** 是否已持有 token（仅表示曾授权成功，不保证未过期）。 */
    val isAuthorized: Boolean get() = accessToken != null

    fun clear() {
        accessToken = null
    }
}
