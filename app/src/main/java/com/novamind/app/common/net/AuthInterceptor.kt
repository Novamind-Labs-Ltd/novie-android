package com.novamind.app.common.net

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 鉴权拦截器：若 [TokenProvider] 有 access token，则统一加上
 * `Authorization: Bearer <token>` 头。已带该头的请求不覆盖。
 */
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = TokenProvider.accessToken
        val request = if (token.isNullOrBlank() || original.header("Authorization") != null) {
            original
        } else {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
