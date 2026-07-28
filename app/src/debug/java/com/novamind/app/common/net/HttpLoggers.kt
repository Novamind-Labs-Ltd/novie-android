package com.novamind.app.common.net

import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Debug 变体：提供 OkHttp 日志拦截器（打印请求/响应体，便于联调抓包）。
 *
 * logging-interceptor 仅以 debugImplementation 引入，故该实现只存在于 debug 源集，
 * release 变体使用 src/release 下的空实现，保证发布包不含日志拦截器。
 *
 * ⚠️ SSE 必须绕开 body 日志，否则流式会被彻底摧毁。`Level.BODY` 下
 * HttpLoggingInterceptor 为了打印响应体会执行
 * `source.request(Long.MAX_VALUE) // Buffer the entire body.`（okhttp 4.12.0 源码原文），
 * 且**没有** text/event-stream 的特例分支。对 SSE 长连来说这句话意味着：它一直阻塞到服务端
 * 把整条流关掉，才把攒满的 body 交给调用方——服务端每帧准时发出的增量，在 App 侧变成
 * "等很久，然后一次性全部出现"。
 *
 * 这道闸放在**这里**而不是放在 SSE 调用方，是因为所有客户端都从
 * [NetworkModule.okHttpClient] 派生：在调用方摘拦截器只能救那一个已经踩坑的调用点，
 * 下一个流式接口还会再踩一遍。在源头按请求放行，新增的流式调用方自动是对的。
 */
object HttpLoggers {

    private val bodyLogger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
        // 别把 Auth0 access token / 设备标识整条打进 logcat。debug-only 不等于可以随便漏：
        // 联调时截图/贴日志是常态，一条完整 Bearer 贴出去就是一个可用的凭证，
        // X-Device-Id 则是稳定的设备标识（CommonHeaders 每个请求都带）。
        redactHeader("Authorization")
        redactHeader("X-Device-Id")
    }

    /**
     * 流式请求降级到 HEADERS 而不是完全不打日志：`Level.HEADERS` 下 `logBody` 为 false，
     * 那个 `source.request(Long.MAX_VALUE)` 分支根本不会进，所以对流式是安全的；同时保住了
     * 请求行 / 状态码 / 响应头 —— 开流前的 401/403 排查全靠这些，完全静音反而不好查。
     */
    private val headersLogger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.HEADERS
        redactHeader("Authorization")
        redactHeader("X-Device-Id")
    }

    fun create(): Interceptor? = Interceptor { chain ->
        val request = chain.request()
        if (request.header("Accept")?.contains("text/event-stream") == true) {
            headersLogger.intercept(chain)  // 流式：只打头，绝不碰响应体
        } else {
            bodyLogger.intercept(chain)
        }
    }
}
