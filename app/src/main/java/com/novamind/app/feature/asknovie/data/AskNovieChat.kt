package com.novamind.app.feature.asknovie.data

import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.ApiConfig
import com.novamind.app.common.net.HttpLoggers
import com.novamind.app.common.net.NetworkModule
import com.novamind.app.common.net.TokenProvider
import com.novamind.app.util.TimeUtils
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** `POST /v1/chat` 请求体（见 my-novie-agents/doc/frontend-api.md）。 */
@Serializable
data class ChatRequest(
    val mode: String,
    @SerialName("conversation_id") val conversationId: String,
    val input: String,
    /** offer-tap 二次进入，仅 brainstorm 有效；MVP 不用，留字段。 */
    val action: String? = null,
)

/** SSE 一帧解析后的事件（events.py：text / card / status / error / done）。 */
sealed interface ChatStreamEvent {
    /** 文本增量（打字机）。 */
    data class TextDelta(val delta: String) : ChatStreamEvent
    /** 交互式卡片（card_type 区分种类）；MVP 暂不渲染，仅透传原始 data。 */
    data class Card(val cardType: String, val data: JsonObject) : ChatStreamEvent
    /** skill 调用状态（thinking / using xxx skill）。 */
    data class Status(val state: String?, val skill: String?, val label: String?) : ChatStreamEvent
    /** 出错；其后必有 [Done]。 */
    data class Failure(val code: String?, val message: String?) : ChatStreamEvent
    /** 一轮结束；finish_reason ∈ stop/turn_limit/error/cancelled。 */
    data class Done(val finishReason: String?) : ChatStreamEvent
}

/**
 * Ask Novie 聊天 SSE 客户端。前端带 Auth0 token 直连 agent 服务（[ApiConfig.agentBaseUrl]），
 * `POST /v1/chat` 返回 `text/event-stream`，本类逐行解析 SSE 帧发为 [ChatStreamEvent] 流。
 *
 * agent 走独立 agents 子域（[ApiConfig.agentBaseUrl]），直接复用应用主客户端（通用头 /
 * AuthInterceptor 自动附带 Auth0 token / 401 刷新），仅把读超时覆盖为 0（SSE 长连不超时）。
 */
object AskNovieChat {
    private const val TAG = "AskNovieChat"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val client: OkHttpClient by lazy {
        NetworkModule.okHttpClient.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS) // SSE 长连：不读超时
            // 摘掉会整体缓冲响应体的拦截器（debug 变体的 HttpLoggingInterceptor(BODY)）。
            // 不摘的话 SSE 就不是流式了：那个拦截器为了打印 body 会 `source.request(Long.MAX_VALUE)`，
            // 一直阻塞到服务端关流才放行——服务端逐帧发的增量，在这里会攒成一坨同时到达，
            // 表现就是"点了发送后长时间没反应，然后整段答案唰地全出来"。本类下面每收到一帧都会
            // 自己打 AppLog（含时间戳），所以这条链路的抓包能力并没有因此丢失。
            .apply { interceptors().removeAll { HttpLoggers.bufsResponseBody(it) } }
            .build()
    }

    /**
     * 发起一轮对话并流式返回。[conversationId] 同值多轮共享短期记忆。
     * 收集被取消时关闭底层连接。任何一轮流最终都以 [ChatStreamEvent.Done] 结束（出错先发 [Failure]）。
     */
    fun streamChat(
        mode: String,
        conversationId: String,
        input: String,
        action: String? = null,
    ): Flow<ChatStreamEvent> = flow {
        val payload = json.encodeToString(ChatRequest(mode, conversationId, input, action))
        val url = ApiConfig.agentBaseUrl + "v1/chat"
        // 请求前日志：不打 input 原文（用户内容，PII），只记长度与关键路由/鉴权状态，便于排查
        // 开流前错误（如 401 invalid_token / 403 not_registered）。hasToken 反映是否会带 Bearer。
        AppLog.i(TAG) {
            "chat 请求前 url=$url env=${ApiConfig.env.label} mode=$mode conv=$conversationId " +
                "action=${action ?: "-"} inputLen=${input.length} " +
                "hasToken=${!TokenProvider.accessToken.isNullOrBlank()}"
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            // Authorization（Auth0 Bearer）由 AuthInterceptor 统一附带，不在此手动设置
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(request)
        // 流被取消（用户停止 / 离开）时取消调用，解除阻塞在 readUtf8Line 的读
        currentCoroutineContext()[Job]?.invokeOnCompletion { runCatching { call.cancel() } }

        try {
            call.execute().use { resp ->
            if (!resp.isSuccessful) {
                // 开流前错误：JSON `{code}` + HTTP 状态码
                val body = runCatching { resp.body?.string() }.getOrNull()
                val code = runCatching {
                    body?.let { json.parseToJsonElement(it).jsonObject["code"]?.jsonPrimitive?.contentOrNull }
                }.getOrNull()
                AppLog.w(TAG) { "chat 开流前错误 http=${resp.code} code=$code" }
                emit(ChatStreamEvent.Failure(code ?: "http_${resp.code}", resp.message))
                emit(ChatStreamEvent.Done("error"))
                return@flow
            }
            AppLog.i(TAG) { "chat 开流成功 http=${resp.code} message=${resp.message}" }
            val source = resp.body?.source()
            if (source == null) {
                emit(ChatStreamEvent.Failure("empty_body", null))
                emit(ChatStreamEvent.Done("error"))
                return@flow
            }

            var eventName: String? = null
            val data = StringBuilder()
            while (currentCoroutineContext().isActive) {
                val line = source.readUtf8Line() ?: break // 连接关闭
                when {
                    line.isEmpty() -> {
                        // 空行 = 一帧结束，分发
                        val name = eventName
                        if (name != null) {
                            val rawData = data.toString()
                            // 记录服务端原始帧，包含文本增量与状态/done，便于还原 SSE 返回。
                            // AppLog 会在写入各 Sink 前统一做 PII 脱敏。
                            val receivedAt = TimeUtils.format(
                                System.currentTimeMillis(),
                                "yyyy-MM-dd HH:mm:ss.SSS",
                            )
                            AppLog.i(TAG) {
                                "chat SSE 收到 time=$receivedAt event=$name data=$rawData"
                            }
                            val ev = parseFrame(name, rawData)
                            if (ev != null) emit(ev)
                            if (ev is ChatStreamEvent.Done) return@flow
                        }
                        eventName = null
                        data.clear()
                    }
                    line.startsWith(":") -> Unit // 注释行忽略
                    line.startsWith("event:") -> eventName = line.substring(6).trim()
                    line.startsWith("data:") -> {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.substring(5).let { if (it.startsWith(" ")) it.substring(1) else it })
                    }
                    // 其它字段（id: / retry:）忽略
                }
            }
            }
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c // 取消（停止/离开）正常传播，不当作错误
        } catch (t: Throwable) {
            AppLog.w(TAG) { "chat 流异常: ${t.message}" }
            emit(ChatStreamEvent.Failure("network_error", t.message))
            emit(ChatStreamEvent.Done("error"))
        }
    }.flowOn(Dispatchers.IO)

    private fun parseFrame(event: String, data: String): ChatStreamEvent? {
        val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
        fun str(key: String) = obj?.get(key)?.jsonPrimitive?.contentOrNull
        return when (event) {
            "text" -> str("delta")?.let { ChatStreamEvent.TextDelta(it) }
            "card" -> str("card_type")?.let { ChatStreamEvent.Card(it, obj ?: return null) }
            "status" -> ChatStreamEvent.Status(str("state"), str("skill"), str("label"))
            "error" -> ChatStreamEvent.Failure(str("code"), str("message"))
            "done" -> ChatStreamEvent.Done(str("finish_reason"))
            else -> null
        }
    }
}
