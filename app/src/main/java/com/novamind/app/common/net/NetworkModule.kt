package com.novamind.app.common.net

import com.novamind.app.debug.apitest.ApiTls
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络层单例：集中构建 [OkHttpClient] 与 [Retrofit]，对外暴露 [apiService]。
 *
 * - 公用头部：[CommonHeadersInterceptor]（复用 [CommonHeaders] 字段口径）；
 * - TLS：复用 [ApiTls] 的钉定主机名校验（对无 SAN 的 IP 接口放宽主机名绑定，仍校验证书指纹）；
 * - 日志：仅 Debug 变体注入（见 [HttpLoggers]，release 为空实现）；
 * - 转换器：kotlinx.serialization（[json]）；动态 URL 场景响应取 ResponseBody 原文，故 baseUrl 仅作占位。
 */
object NetworkModule {

    /**
     * baseUrl 取自 [ApiConfig]（可在 Debug 工具箱切换域名）。当前接口多用 `@Url` 绝对地址，
     * 故此值主要供后续相对路径接口拼接；Retrofit 实例懒加载，切换域名下次冷启动生效。
     */
    private val baseUrl: String get() = ApiConfig.baseUrl

    /** 全局 JSON 解析配置：容忍未知字段、按需省略默认值，供后续接入类型化响应模型复用。 */
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .hostnameVerifier(ApiTls.PINNED_HOSTNAME_VERIFIER)
            .addInterceptor(CommonHeadersInterceptor())
            .apply { HttpLoggers.create()?.let(::addInterceptor) }
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val apiService: ApiService by lazy { retrofit.create(ApiService::class.java) }
}
