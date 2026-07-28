package com.novamind.app.feature.asknovie.data

import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.ApiConfig
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 * AuthInterceptor 自动附带 Auth0 token / 401 刷新），仅把读超时放宽到 [SSE_READ_TIMEOUT_S]
 * 以容纳长连（不设 0：那样半开连接会永久阻塞，理由见 client）。
 * debug 变体的 body 日志由 [com.novamind.app.common.net.HttpLoggers] 按 Accept 头对流式放行，
 * 不在这里摘拦截器。
 */
object AskNovieChat {
    private const val TAG = "AskNovieChat"

    /** SSE 读超时（秒）。服务端心跳 15s，取 4 倍余量；绝不设 0，理由见 [client]。 */
    private const val SSE_READ_TIMEOUT_S = 60L

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val client: OkHttpClient by lazy {
        NetworkModule.okHttpClient.newBuilder()
            // SSE 长连：读超时要远大于服务端心跳间隔，但**不能是 0**。服务端用
            // sse_starlette 的默认 ping 间隔 15s（`EventSourceResponse.DEFAULT_PING_INTERVAL`，
            // 后端未覆盖该参数），心跳是 `:` 注释帧、下面的解析循环会忽略掉，但它会刷新这个
            // 读超时。设 0（永不超时）的话，遇到半开连接（切网/基站漂移/NAT 静默丢弃）
            // readUtf8Line 会永久阻塞：既不报错也不结束，调用方的 finally 不跑，会话就永远
            // 卡在"正在回复"。60s = 4 倍心跳，正常流不可能触发。
            .readTimeout(SSE_READ_TIMEOUT_S, TimeUnit.SECONDS)
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
            // 刻意**不设** Accept-Encoding。曾经加过 `identity` 防"网关压缩 SSE 导致攒帧",
            // 但那是没有证据的猜测,而代价是实打实的:OkHttp 的 BridgeInterceptor 只在调用方
            // **没有**设这个头时才置 transparentGzip=true 并负责解压(BridgeInterceptor.kt:69/90);
            // 手动设了就等于永久关掉它的透明解压。万一网关无视 identity 照样压,收到的就是原始
            // gzip 字节 —— 每一行都匹配不上 event:/data:,用户拿到空气泡 + stream_truncated,
            // 而不设这个头的话 OkHttp 本来能正确解压。用一个真实的静默失败去换一个假想收益。
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
                // `resp.message` 必须过一道 isNotBlank：HTTP/2 下它**恒为空串**而不是 null。
                // OkHttp 的 Http2ExchangeCodec 用 `StatusLine.parse("HTTP/1.1 $status")` 造响应,
                // 只有状态码没有 reason phrase,于是 message="" —— 而消费方写的是
                // `event.message ?: 友好兜底`,空串是非 null,兜底永远不触发,气泡就是**全空**的:
                // 用户发完消息看到一个没有任何文字、也没有任何报错的助手气泡。
                // NetworkModule 没有限制 protocols,默认 [HTTP_2, HTTP_1_1],现代网关一律协商上 h2,
                // 所以这条路是常态不是边角。后端错误响应体里也只有 `code`、从不带 message。
                emit(ChatStreamEvent.Failure(
                    code ?: "http_${resp.code}",
                    resp.message.takeIf { it.isNotBlank() },
                ))
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
            // 走到这里 = 循环 break 了(readUtf8Line 返回 null:连接被干净地关掉)，而**没有**
            // 收到 done 帧 —— 收到 done 的正常路径在上面就 `return@flow` 了。
            // 服务端进程重启 / 网关 idle 超时 / LB 排空都会这样：TCP 正常 FIN，没有异常，
            // 读超时也不会触发。不补这一下的话,flow 正常结束、调用方 finally 把半截回答当成
            // 完整回答存进 ChatSessionStore,用户看到一个被截断却毫无提示的答案。
            // 这条也是本类 KDoc「任何一轮流最终都以 Done 结束」那句承诺的兑现。
            // isActive 判一下:循环也可能是因为**用户主动停止**(协程被取消)才退出的,
            // 那种情况不是截断,不该给用户报错。
            if (currentCoroutineContext().isActive) {
                AppLog.w(TAG) { "chat 流被提前关闭(未收到 done 帧)" }
                emit(ChatStreamEvent.Failure("stream_truncated", null))
                emit(ChatStreamEvent.Done("error"))
            }
            }
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c // 取消（停止/离开）正常传播，不当作错误
        } catch (t: Throwable) {
            AppLog.w(TAG) { "chat 流异常: ${t.message}" }
            // message 传 null,不把 `t.message` 递给 UI:那是给开发者看的异常文本
            // (SocketTimeoutException 的 "timeout"、"unexpected end of stream" 之类),
            // 消费方会 `event.message ?: 友好兜底`,传原文等于把它直接印进聊天气泡。
            // 原文已经在上面那行日志里了,排查不受影响。
            emit(ChatStreamEvent.Failure("network_error", null))
            emit(ChatStreamEvent.Done("error"))
        }
    }.flowOn(Dispatchers.IO)

    private fun parseFrame(event: String, data: String): ChatStreamEvent? {
        val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
        fun str(key: String) = obj?.get(key)?.jsonPrimitive?.contentOrNull
        return when (event) {
            "text" -> (obj?.get("delta") ?: obj?.get("text"))
                ?.let(::extractDeltaText)
                ?.takeIf { it.isNotEmpty() }
                ?.let(ChatStreamEvent::TextDelta)
            "card" -> str("card_type")?.let { ChatStreamEvent.Card(it, obj ?: return null) }
            "status" -> ChatStreamEvent.Status(str("state"), str("skill"), str("label"))
            "error" -> ChatStreamEvent.Failure(str("code"), str("message"))
            "done" -> ChatStreamEvent.Done(str("finish_reason"))
            else -> null
        }
    }

    /**
     * Agent 文本 delta 通常是普通字符串，语音场景也可能返回
     * `{"text":"...","begin_time":...}` 对象，或该对象的 JSON 字符串。
     * 若 text 内含 sentences 数组，按顺序展开 sentences[].sentence.text。
     * UI 只消费文字，时间戳等结构化字段不进入消息正文。
     */
    private fun extractDeltaText(element: JsonElement): String? {
        val text = extractDeltaTextValue(element)
        AppLog.i(TAG) { "chat SSE delta 提取结果 text=${text.orEmpty()}" }
        return text
    }

    /** 递归展开结构化 delta；与上层日志分离，避免每个 sentence 重复打印。 */
    private fun extractDeltaTextValue(element: JsonElement): String? = when (element) {
        is JsonObject -> (
            element["sentences"]
                ?: element["sentence"]
                ?: element["text"]
            )?.let(::extractDeltaTextValue)
        is JsonArray -> element
            .mapNotNull(::extractDeltaTextValue)
            .joinToString(separator = "")
            .takeIf { it.isNotEmpty() }
        is JsonPrimitive -> {
            val raw = element.contentOrNull ?: return null
            val nested = runCatching { json.parseToJsonElement(raw) }.getOrNull()
            if (nested != null && nested !is JsonPrimitive) extractDeltaTextValue(nested) else raw
        }
    }
}
