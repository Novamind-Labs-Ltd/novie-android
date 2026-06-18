package com.novamind.app.common.net

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 把 [CommonHeaders] 公用头部注入每个 OkHttp 请求。
 *
 * 复用 [CommonHeaders.snapshot] 的字段口径（平台/版本/设备标识/语言/时区/链路追踪），
 * 仅当请求未显式设置同名头时才补充，避免覆盖调用方（如 Retrofit `@Header`）的定制值。
 *
 * @param tokenProvider 访问令牌提供器；返回非空时附带 `Authorization: Bearer <token>`。
 *                      默认无 token（匿名请求），接入真实登录态时注入 Auth0 token 取值逻辑。
 */
class CommonHeadersInterceptor(
    private val tokenProvider: () -> String? = { null },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
        CommonHeaders.snapshot(token = tokenProvider()).forEach { (name, value) ->
            if (original.header(name) == null) builder.header(name, value)
        }
        return chain.proceed(builder.build())
    }
}
