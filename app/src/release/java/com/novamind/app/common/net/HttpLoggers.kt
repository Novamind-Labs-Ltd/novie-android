package com.novamind.app.common.net

import okhttp3.Interceptor

/**
 * Release 变体：不提供日志拦截器（发布包不应打印网络明文）。
 * 与 src/debug 下的实现签名一致，由 [NetworkModule] 统一按需添加。
 * 这里恒为 null，所以 debug 变体里那条「SSE 绕开 body 日志」的闸在 release 无事可做。
 */
object HttpLoggers {
    fun create(): Interceptor? = null
}
