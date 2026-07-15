package com.novamind.app.common.net

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * 401 自动续期重试：请求带的 access token 过期被后端拒（HTTP 401）时，用 [TokenProvider.renew]
 * 静默续期（refresh_token），拿到新 token 后**重试一次**原请求。
 *
 * - 仅重试一次：靠 [priorResponse] 链计数，避免续期后仍 401 造成死循环。
 * - 并发去重：多个请求同时 401 时加锁；若别的线程已把 [TokenProvider.accessToken] 刷成新值，
 *   直接用新值重试，不重复续期。
 * - 续期失败（refresh_token 失效等）返回 null 放弃重试；失效登出由 [TokenProvider.renew] 的注入方触发。
 *
 * Authenticator 运行在 OkHttp 的 IO 线程，允许阻塞，故可在此同步续期。
 */
class TokenAuthenticator : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // 已经重试过一次（响应链里有前序响应）→ 放弃，避免死循环。
        if (responseCount(response) >= 2) return null

        val failedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")?.trim()
        // 原请求本就没带 token（游客 / 已登出）→ 不是 token 过期，不触发续期。
        if (failedToken.isNullOrBlank()) return null

        val fresh = synchronized(lock) {
            val current = TokenProvider.accessToken
            // 其他线程已续期出新 token → 直接用；否则触发一次续期。
            if (!current.isNullOrBlank() && current != failedToken) current
            else TokenProvider.renew?.invoke()
        }

        // 续期失败，或拿到的仍是那个已失效的 token → 不重试。
        if (fresh.isNullOrBlank() || fresh == failedToken) return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $fresh")
            .build()
    }

    /** 统计响应链长度（含本次）：>=2 说明已重试过。 */
    private fun responseCount(response: Response): Int {
        var prior = response.priorResponse
        var count = 1
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
