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
     * baseUrl 取自 [ApiConfig] 的唯一 api 域名（可在 Debug 工具箱切换 test/prod 环境）。当前接口多用
     * `@Url` 绝对地址，故此值主要供后续相对路径接口拼接；Retrofit 懒加载，切换下次冷启动生效。
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
            // 401 → 用 refresh_token 静默续期并重试一次（续期钩子由认证层注入）
            .authenticator(TokenAuthenticator())
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

    /** 认证类型化接口（现统一走 api 域）。lazy → 切换环境下次冷启动生效。 */
    val authApi: AuthApi by lazy { retrofit(ApiConfig.apiBaseUrl).create(AuthApi::class.java) }

    /** 身份档案接口（/api/v1.0/me），走统一响应信封，供全局用户会话的档案源。 */
    val identityApi: IdentityApi by lazy { retrofit(ApiConfig.apiBaseUrl).create(IdentityApi::class.java) }

    /** 文件域（/api/v1.0/files）类型化接口，走统一响应信封。 */
    val filesApi: FilesApi by lazy { retrofit(ApiConfig.apiBaseUrl).create(FilesApi::class.java) }

    /** 笔记域（/api/v1.0/notes）类型化接口，走统一响应信封。 */
    val notesApi: NotesApi by lazy { retrofit(ApiConfig.apiBaseUrl).create(NotesApi::class.java) }

    /** 文件夹域（/api/v1.0/folders）类型化接口，走统一响应信封。 */
    val foldersApi: FoldersApi by lazy { retrofit(ApiConfig.apiBaseUrl).create(FoldersApi::class.java) }

    /** 笔记附件（/api/v1.0/notes/{id}/attachments）类型化接口，走统一响应信封。 */
    val attachmentsApi: AttachmentsApi by lazy { retrofit(ApiConfig.apiBaseUrl).create(AttachmentsApi::class.java) }

    /** 笔记源录音（/api/v1.0/notes/{id}/audio，§7 断点续传）类型化接口，走统一响应信封。 */
    val noteAudioApi: NoteAudioApi by lazy { retrofit(ApiConfig.apiBaseUrl).create(NoteAudioApi::class.java) }

    /** 转写结果 / 状态（/api/v1.0/notes/{id}/transcription，§9 前端轮询）类型化接口。 */
    val transcriptionApi: TranscriptionApi by lazy { retrofit(ApiConfig.apiBaseUrl).create(TranscriptionApi::class.java) }

    /** Ask Novie 短语音同步转写（/api/v1.0/transcribe）。 */
    val askNovieTranscribeApi: AskNovieTranscribeApi by lazy {
        retrofit(ApiConfig.apiBaseUrl).create(AskNovieTranscribeApi::class.java)
    }

    /** 笔记 AI 润色：前端携 Auth0 token 直连 agent 的一次性 JSON 接口。 */
    val polishApi: PolishApi by lazy {
        retrofit(ApiConfig.agentBaseUrl).create(PolishApi::class.java)
    }

    /** Ask Novie 会话级图片/PDF 附件。 */
    val conversationAttachmentsApi: ConversationAttachmentsApi by lazy {
        retrofit(ApiConfig.apiBaseUrl).create(ConversationAttachmentsApi::class.java)
    }

    /** 后端日历事件及事件-笔记绑定接口。 */
    val calendarBackendApi: CalendarBackendApi by lazy {
        retrofit(ApiConfig.apiBaseUrl).create(CalendarBackendApi::class.java)
    }
}
