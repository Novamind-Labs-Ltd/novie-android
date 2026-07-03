package com.novamind.app.common.net

import com.novamind.app.common.config.AppConfig
import com.novamind.app.common.net.ApiTls
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
     * baseUrl 取自 [ApiConfig] 的通用接口域名（可在 Debug 工具箱切换环境）。当前接口多用
     * `@Url` 绝对地址，故此值主要供后续相对路径接口拼接；Retrofit 懒加载，切换下次冷启动生效。
     * auth / chat 两类域名由各自的调用层读取 [ApiConfig.authBaseUrl] / [ApiConfig.chatBaseUrl]。
     */
    private val baseUrl: String get() = ApiConfig.apiBaseUrl

    /** 全局 JSON 解析配置：容忍未知字段、按需省略默认值，供后续接入类型化响应模型复用。 */
    internal val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(AppConfig.Network.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AppConfig.Network.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(AppConfig.Network.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .hostnameVerifier(ApiTls.PINNED_HOSTNAME_VERIFIER)
            .addInterceptor(CommonHeadersInterceptor())
            .addInterceptor(AuthInterceptor())
            .apply { HttpLoggers.create()?.let(::addInterceptor) }
            .build()
    }

    /** 按 baseUrl 构建 Retrofit，共用同一 [okHttpClient]。 */
    private fun retrofit(url: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(url)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    /** 通用接口（@Url 绝对地址；供调试工具与历史调用）。 */
    val apiService: ApiService by lazy { retrofit(baseUrl).create(ApiService::class.java) }

    /** 认证域（auth.*）类型化接口。lazy → 切换环境下次冷启动生效。 */
    val authApi: AuthApi by lazy { retrofit(ApiConfig.authBaseUrl).create(AuthApi::class.java) }
}
