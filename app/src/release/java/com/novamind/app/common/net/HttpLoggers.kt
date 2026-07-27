package com.novamind.app.common.net

import okhttp3.Interceptor

/**
 * Release 变体：不提供日志拦截器（发布包不应打印网络明文）。
 * 与 src/debug 下的实现签名一致，由 [NetworkModule] 统一按需添加。
 */
object HttpLoggers {
    fun create(): Interceptor? = null

    /** Release 包没有日志拦截器，不存在缓冲响应体的问题。签名与 debug 变体一致。 */
    fun bufsResponseBody(interceptor: Interceptor): Boolean = false
}
