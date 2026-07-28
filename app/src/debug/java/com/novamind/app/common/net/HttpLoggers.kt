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

    /** SSE 长连需移除所有网络日志拦截器，避免读取或干预持续响应流。 */
    fun isLoggingInterceptor(interceptor: Interceptor): Boolean =
        interceptor is HttpLoggingInterceptor
}
