package com.novamind.app.common.net

import okhttp3.Interceptor

/**
 * Release 变体：不提供日志拦截器（发布包不应打印网络明文）。
 * 与 src/debug 下的实现签名一致，由 [NetworkModule] 统一按需添加。
 */
object HttpLoggers {
    fun create(): Interceptor? = null

    /** Release 变体不包含 logging-interceptor，保持与 Debug 源集的 API 一致。 */
    fun isBodyLoggingInterceptor(interceptor: Interceptor): Boolean = false
}
