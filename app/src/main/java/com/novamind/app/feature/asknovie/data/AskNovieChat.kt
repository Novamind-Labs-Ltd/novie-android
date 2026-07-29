package com.novamind.app.feature.asknovie.data

import android.os.SystemClock
import com.novamind.app.BuildConfig
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
    @SerialName("attachment_ids") val attachmentIds: List<String> = emptyList(),
    /** offer-tap 二次进入，仅 brainstorm 有效；MVP 不用，留字段。 */
    val action: String? = null,
)

/** SSE 一帧解析后的事件（events.py：text / card / status / error / done）。 */
sealed interface ChatStreamEvent {
    /** 文本增量（打字机）。 */
    data class TextDelta(val delta: String) : ChatStreamEvent
    /** 交互式卡片（card_type 区分种类）。 */
    data class Card(val card: ChatCard) : ChatStreamEvent
    /** skill 调用状态（thinking / using xxx skill）。 */
    data class Status(val state: String?, val skill: String?, val label: String?) : ChatStreamEvent
    /** 出错；其后必有 [Done]。 */
    data class Failure(val code: String?, val message: String?) : ChatStreamEvent
    /** 一轮结束；finish_reason ∈ stop/turn_limit/error/cancelled。 */
    data class Done(val finishReason: String?) : ChatStreamEvent
}

data class OptionItem(
    val id: String,
    val label: String,
    val description: String = "",
)

sealed interface ChatCard {
    data class Options(
        val prompt: String,
        val items: List<OptionItem>,
        val allowFreeText: Boolean,
        /** 未知值按单选处理，保持向前兼容。 */
        val select: String,
    ) : ChatCard

    data class Diagram(val diagramType: String, val mermaid: String, val caption: String) : ChatCard
    data class Summary(val title: String, val body: String, val saveable: Boolean) : ChatCard
    data class Offer(val kind: String, val label: String) : ChatCard
    data class CreateNote(val draftTitle: String, val draftContent: String) : ChatCard
    data class SaveNote(val draftTitle: String, val draftContent: String) : ChatCard
    data class Note(val noteId: String, val title: String) : ChatCard
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
            .apply {
                // newBuilder() 会继承共享 client 的网络日志拦截器。SSE 必须直接消费响应流，
                // 因此此专用副本不保留任何 HttpLoggingInterceptor。
                interceptors().removeAll(HttpLoggers::isLoggingInterceptor)
            }
            .readTimeout(0, TimeUnit.SECONDS) // SSE 长连：不读超时
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
        attachmentIds: List<String> = emptyList(),
        action: String? = null,
    ): Flow<ChatStreamEvent> = flow {
        val payload = json.encodeToString(
            ChatRequest(mode, conversationId, input, attachmentIds, action),
        )
        val url = ApiConfig.agentBaseUrl + "v1/chat"
        // 请求前日志：不打 input 原文（用户内容，PII），只记长度与关键路由/鉴权状态，便于排查
        // 开流前错误（如 401 invalid_token / 403 not_registered）。hasToken 反映是否会带 Bearer。
        AppLog.i(TAG) {
            "chat 请求前 url=$url env=${ApiConfig.env.label} mode=$mode conv=$conversationId " +
                "action=${action ?: "-"} inputLen=${input.length} " +
                "attachmentCount=${attachmentIds.size} " +
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
            if (BuildConfig.DEBUG) {
                // 不使用 HttpLoggingInterceptor：只读取已建立请求的 Header，
                // 不触碰 response body，避免 SSE 被整体缓冲。
                AppLog.i(TAG) { "chat HTTP request headers=${resp.request.headers}" }
                AppLog.i(TAG) { "chat HTTP response headers=${resp.headers}" }
            }
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
            AppLog.i(TAG) {
                "chat 开流成功 http=${resp.code} httpMessage=${resp.message}" +
                    if (BuildConfig.DEBUG) " message=$input" else ""
            }
            val source = resp.body?.source()
            if (source == null) {
                emit(ChatStreamEvent.Failure("empty_body", null))
                emit(ChatStreamEvent.Done("error"))
                return@flow
            }

            val streamOpenedAt = SystemClock.elapsedRealtime()
            var lastFrameAt = streamOpenedAt
            var frameCount = 0
            var textFrameCount = 0
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
                            val frameAt = SystemClock.elapsedRealtime()
                            frameCount++
                            val gapMs = frameAt - lastFrameAt
                            val elapsedMs = frameAt - streamOpenedAt
                            lastFrameAt = frameAt
                            AppLog.i(TAG) {
                                "chat SSE 帧 #$frameCount event=$name elapsedMs=$elapsedMs " +
                                    "gapMs=$gapMs dataChars=${rawData.length}"
                            }
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
                            if (ev is ChatStreamEvent.TextDelta) textFrameCount++
                            if (ev != null) emit(ev)
                            if (ev is ChatStreamEvent.Done) {
                                AppLog.i(TAG) {
                                    "chat SSE 结束 reason=done frames=$frameCount " +
                                        "textFrames=$textFrameCount durationMs=$elapsedMs"
                                }
                                return@flow
                            }
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
            AppLog.i(TAG) {
                "chat SSE 结束 reason=connection_closed frames=$frameCount " +
                    "textFrames=$textFrameCount " +
                    "durationMs=${SystemClock.elapsedRealtime() - streamOpenedAt}"
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
            "text" -> (obj?.get("delta") ?: obj?.get("text"))
                ?.let(::extractDeltaText)
                ?.takeIf { it.isNotEmpty() }
                ?.let(ChatStreamEvent::TextDelta)
            "card" -> obj?.let(::parseCard)?.let(ChatStreamEvent::Card)
            "status" -> ChatStreamEvent.Status(str("state"), str("skill"), str("label"))
            "error" -> ChatStreamEvent.Failure(str("code"), str("message"))
            "done" -> ChatStreamEvent.Done(str("finish_reason"))
            else -> null
        }
    }

    /** 未知 card_type 直接跳过；已知类型只读取需要的字段，自然忽略新增字段。 */
    internal fun parseCard(obj: JsonObject): ChatCard? {
        fun str(key: String) = obj[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        fun bool(key: String, default: Boolean) =
            obj[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: default

        return when (str("card_type")) {
            "options" -> {
                val items = (obj["items"] as? JsonArray).orEmpty().mapNotNull { element ->
                    val item = element as? JsonObject ?: return@mapNotNull null
                    val label = item["label"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (label.isBlank()) return@mapNotNull null
                    OptionItem(
                        id = item["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        label = label,
                        description = item["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    )
                }
                ChatCard.Options(
                    prompt = str("prompt"),
                    items = items,
                    allowFreeText = bool("allow_free_text", true),
                    select = str("select").takeIf { it == "many" } ?: "one",
                )
            }
            "diagram" -> ChatCard.Diagram(str("diagram_type"), str("mermaid"), str("caption"))
            "summary" -> ChatCard.Summary(str("title"), str("body"), bool("saveable", true))
            "offer" -> ChatCard.Offer(str("kind"), str("label"))
            "create_note" -> ChatCard.CreateNote(str("draft_title"), str("draft_content"))
            "save_note" -> ChatCard.SaveNote(str("draft_title"), str("draft_content"))
            "note" -> str("note_id").takeIf(String::isNotBlank)?.let { ChatCard.Note(it, str("title")) }
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
