package com.novamind.app.common.net

import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Debug 变体：提供 OkHttp 日志拦截器（打印请求/响应体，便于联调抓包）。
 *
 * logging-interceptor 仅以 debugImplementation 引入，故该实现只存在于 debug 源集，
 * release 变体使用 src/release 下的空实现，保证发布包不含日志拦截器。
 */
object HttpLoggers {
    fun create(): Interceptor? = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    /** SSE 专用：只打印 Header，不读取长连接 Body；Debug 联调保留 Authorization。 */
    fun createHeaders(): Interceptor? = HttpLoggingInterceptor().apply {
        redactHeader("Cookie")
        redactHeader("Set-Cookie")
        level = HttpLoggingInterceptor.Level.HEADERS
    }

    /** SSE 长连需移除 BODY logger，避免它读完整个响应后才交给消费者。 */
    fun isBodyLoggingInterceptor(interceptor: Interceptor): Boolean =
        interceptor is HttpLoggingInterceptor &&
            interceptor.level == HttpLoggingInterceptor.Level.BODY
}
