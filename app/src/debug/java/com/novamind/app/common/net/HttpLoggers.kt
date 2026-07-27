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

    /**
     * 这个拦截器会不会把响应体整个缓冲下来（从而**摧毁流式**）。
     *
     * `Level.BODY` 下 HttpLoggingInterceptor 为了打印响应体，会执行
     * `source.request(Long.MAX_VALUE) // Buffer the entire body.`（okhttp 4.12.0 源码原文），
     * 且**没有** text/event-stream 的特例分支。对 SSE 长连来说这句话意味着：它会一直阻塞到
     * 服务端把整条流关掉，才把已经攒满的 body 交给调用方——于是服务端每 100ms 准时发出的
     * 增量帧，在 App 这边变成"等很久，然后一次性全部出现"。
     *
     * 因此 SSE 客户端（见 AskNovieChat）必须用这个判定把它摘掉。做成函数而不是让调用方直接
     * `it is HttpLoggingInterceptor`，是因为 logging-interceptor 只以 debugImplementation 引入，
     * main 源集根本引用不到这个类型；release 变体返回 false（那边压根没有日志拦截器）。
     */
    fun bufsResponseBody(interceptor: Interceptor): Boolean = interceptor is HttpLoggingInterceptor
}
