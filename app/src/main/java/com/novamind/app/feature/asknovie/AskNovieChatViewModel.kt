package com.novamind.app.feature.asknovie

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.data.RemoteNoteRepository
import com.novamind.app.feature.asknovie.data.AskNovieChat
import com.novamind.app.feature.asknovie.data.AskNovieAttachmentRepository
import com.novamind.app.feature.asknovie.data.AskNovieTranscriptionRepository
import com.novamind.app.feature.asknovie.data.AskNovieTranscriptionRepository.VoiceTranscription
import com.novamind.app.feature.asknovie.data.ChatStreamEvent
import com.novamind.app.feature.asknovie.data.ChatCard
import com.novamind.app.feature.create.editor.NoteDocument
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class NoteCardPreview(
    val title: String,
    val body: String,
    val updatedAt: String?,
)

/** 从云端 Note 的 content 信封中提取卡片正文；旧数据或异常结构回退 preview/草稿。 */
internal fun noteCardBody(content: String, preview: String?, fallback: String = ""): String {
    val document = runCatching {
        Json.parseToJsonElement(content).jsonObject["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }.getOrDefault("")
    return NoteDocument.previewText(document).trim()
        .ifBlank { preview.orEmpty().trim() }
        .ifBlank { NoteDocument.previewText(fallback).trim() }
}

private const val DISPLAY_CHUNK_SIZE = 4
private const val DISPLAY_INTERVAL_MS = 64L

/**
 * Ask Novie 的 Activity 级会话状态。
 *
 * 每个会话拥有独立的 SSE Job 和消息快照：切换历史只切换正在展示的会话，
 * 不会停止旧会话的服务端回复；旧流结束后仍会写回对应的历史记录。
 */
@HiltViewModel
class AskNovieChatViewModel @Inject constructor(
    application: Application,
    private val notesRepository: RemoteNoteRepository,
) : AndroidViewModel(application) {
    val messages = mutableStateOf<List<ChatMessage>>(emptyList())
    val streamingText = mutableStateOf("")
    val isResponding = mutableStateOf(false)
    val isStreaming = mutableStateOf(false)
    val sessionId = mutableStateOf(UUID.randomUUID().toString())
    val customTitle = mutableStateOf<String?>(null)
    val notePreviews = mutableStateOf<Map<String, NoteCardPreview>>(emptyMap())

    private val sessionMessages = mutableMapOf<String, List<ChatMessage>>()

    /**
     * 每个会话最后已知的标题快照，与 [sessionMessages] 同一套生命周期。
     *
     * 存在的理由:[customTitle] 只有一个,描述的永远是**当前展示**的那个会话。而后台会话的流
     * 结束时,`finally` 会为它调用 [persistSession] —— 那时 `sessionId.value` 可能早就是别的
     * 会话了,于是取不到它的标题,只能退回"第一句话",把用户改过的名字覆盖掉。
     * 切走一个会话时顺手把它的标题存进来(与切走时存 messages 完全对称),就补上了这个缺口。
     *
     * 用 `String?` 而不是只存非空值:用户可以把标题改回空(重命名对话框里
     * `newTitle.ifBlank { null }`),那时必须记住"没有自定义标题",而不是留着上一次的旧值。
     */
    private val sessionTitles = mutableMapOf<String, String?>()
    private val streamJobs = mutableMapOf<String, Job>()
    private val respondingSessions = mutableSetOf<String>()
    private val streamingSessions = mutableSetOf<String>()
    private val sessionStreamingTexts = mutableMapOf<String, String>()
    private var handledNewSessionRequestId = 0L
    private val loadingNotePreviews = mutableSetOf<String>()

    fun ensureNotePreviews(noteIds: Set<String>) {
        noteIds.filter { it.isNotBlank() && it !in notePreviews.value && loadingNotePreviews.add(it) }
            .forEach { noteId ->
                viewModelScope.launch {
                    try {
                        when (val result = notesRepository.getNote(noteId)) {
                            is ApiResult.Success -> result.data?.let { note ->
                                notePreviews.value = notePreviews.value + (
                                    noteId to NoteCardPreview(
                                        title = note.title.orEmpty(),
                                        body = noteCardBody(note.content, note.preview),
                                        updatedAt = note.updatedAt,
                                    )
                                )
                            }
                            is ApiResult.BizError,
                            is ApiResult.NetworkError,
                            -> Unit
                        }
                    } finally {
                        loadingNotePreviews -= noteId
                    }
                }
            }
    }

    var seeded = false
        private set

    fun markSeeded() {
        seeded = true
        sessionMessages[sessionId.value] = messages.value
    }

    /** 上传 Ask Novie 短语音并同步取回转写文字，不自动发送消息。 */
    suspend fun transcribeVoice(path: String, durationSeconds: Int): ApiResult<VoiceTranscription> =
        AskNovieTranscriptionRepository.transcribe(path, durationSeconds)

    /** 图片选择完成后立即上传并关联当前会话。 */
    suspend fun uploadAttachment(attachment: Attachment): Result<String> =
        AskNovieAttachmentRepository.uploadAndAttach(sessionId.value, attachment)

    /** Summary 一键保存：先隐藏入口防止重复落库，再复用当前会话的 `/v1/chat` SSE。 */
    fun saveSummary(card: ChatCard.Summary) {
        val targetSessionId = sessionId.value
        if (streamJobs.containsKey(targetSessionId)) return
        updateSessionMessages(targetSessionId) { current ->
            current.map { message ->
                if (message.card == card) message.copy(card = card.copy(saveable = false)) else message
            }
        }
        startStreamingReply(prompt = "", action = "save_note")
    }

    /** 记录选项卡已提交或关闭，避免页面重建和历史会话恢复后重复弹出。 */
    fun markChoiceCardHandled(index: Int) {
        val targetSessionId = sessionId.value
        updateSessionMessages(targetSessionId) { current ->
            if (index !in current.indices || current[index].card == null) current
            else current.toMutableList().also { it[index] = it[index].copy(cardHandled = true) }
        }
        persistSession(targetSessionId)
    }

    /** 同一个首页进入事件只消费一次，避免页面重新进入组合时重复创建空会话。 */
    fun consumeNewSessionRequest(requestId: Long): Boolean {
        if (requestId <= 0L || requestId == handledNewSessionRequestId) return false
        handledNewSessionRequestId = requestId
        return true
    }

    /** 开始当前会话的 SSE；其他历史会话的在途回复不受影响。 */
    fun startStreamingReply(
        prompt: String,
        attachments: List<Attachment> = emptyList(),
        action: String? = null,
    ) {
        val targetSessionId = sessionId.value
        if (streamJobs.containsKey(targetSessionId)) return

        sessionMessages[targetSessionId] = messages.value
        respondingSessions += targetSessionId
        refreshActiveSession(targetSessionId)

        val job = viewModelScope.launch {
            var textMessageIndex: Int? = null
            var noteStatusIndex: Int? = null
            var streamFailure: ChatStreamEvent.Failure? = null

            fun removeNoteStatus() {
                val index = noteStatusIndex ?: return
                updateSessionMessages(targetSessionId) { current ->
                    if (index !in current.indices) current else current.toMutableList().also {
                        it.removeAt(index)
                    }
                }
                noteStatusIndex = null
            }

            fun ensureBubble() {
                if (textMessageIndex == null) {
                    respondingSessions -= targetSessionId
                    streamingSessions += targetSessionId
                    textMessageIndex = sessionMessages[targetSessionId].orEmpty().size
                    updateSessionMessages(targetSessionId) { it + ChatMessage(Role.Assistant, "") }
                    refreshActiveSession(targetSessionId)
                }
            }

            // 网络收帧与 UI 打字机解耦：SSE collector 只入队，不被显示节奏反向阻塞。
            // UNLIMITED 保证后端突发输出时不丢 delta；单消费者严格保持原始顺序。
            val displayQueue = Channel<ChatStreamEvent>(capacity = Channel.UNLIMITED)
            val displayJob = launch {
                for (event in displayQueue) {
                    when (event) {
                        is ChatStreamEvent.TextDelta -> {
                            removeNoteStatus()
                            ensureBubble()
                            event.delta.displayChunks(DISPLAY_CHUNK_SIZE).forEach { chunk ->
                                val updatedText = sessionStreamingTexts[targetSessionId].orEmpty() + chunk
                                sessionStreamingTexts[targetSessionId] = updatedText
                                if (sessionId.value == targetSessionId) {
                                    streamingText.value = updatedText
                                }
                                delay(DISPLAY_INTERVAL_MS)
                            }
                        }
                        is ChatStreamEvent.Card -> updateSessionMessages(targetSessionId) { current ->
                            val statusIndex = noteStatusIndex
                            if (event.card is ChatCard.Note && statusIndex != null && statusIndex in current.indices) {
                                current.toMutableList().also {
                                    it[statusIndex] = ChatMessage(Role.Assistant, "", card = event.card)
                                }.also { noteStatusIndex = null }
                            } else {
                                current + ChatMessage(Role.Assistant, "", card = event.card)
                            }
                        }
                        is ChatStreamEvent.Status -> {
                            if (event.skill == "note" && noteStatusIndex == null) {
                                noteStatusIndex = sessionMessages[targetSessionId].orEmpty().size
                                updateSessionMessages(targetSessionId) {
                                    it + ChatMessage(
                                        role = Role.Assistant,
                                        text = "",
                                        block = ChatBlock.SkillStatus(
                                            event.label?.takeIf(String::isNotBlank)
                                                ?: "Creating notes now…",
                                        ),
                                    )
                                }
                            }
                        }
                        is ChatStreamEvent.Failure,
                        is ChatStreamEvent.Done,
                        -> Unit
                    }
                }
            }

            try {
                val attachmentIds = AskNovieAttachmentRepository
                    .prepareForTurn(targetSessionId, attachments)
                    .getOrElse { error ->
                        streamFailure = ChatStreamEvent.Failure(
                            code = "attachment_upload_failed",
                            message = error.message ?: "Could not upload the attachment. Please retry.",
                        )
                        emptyList()
                    }

                if (streamFailure == null) {
                    AskNovieChat.streamChat(
                        mode = "chat",
                        conversationId = targetSessionId,
                        input = prompt,
                        attachmentIds = attachmentIds,
                        action = action,
                    ).collect { event ->
                        when (event) {
                            is ChatStreamEvent.TextDelta,
                            is ChatStreamEvent.Card,
                            is ChatStreamEvent.Status,
                            -> displayQueue.send(event)
                            is ChatStreamEvent.Failure -> {
                                streamFailure = event
                            }
                            is ChatStreamEvent.Done,
                            -> Unit
                        }
                    }
                }
                // 正常结束时先把队列中已收到的文字播完，再收束 streaming 状态。
                displayQueue.close()
                displayJob.join()

                streamFailure?.let { failure ->
                    removeNoteStatus()
                    ensureBubble()
                    val message = when (failure.code) {
                        "grilling_unavailable" ->
                            "Deep questioning is temporarily unavailable. Please try again."
                        else -> failure.message
                            ?: "Something went wrong (${failure.code ?: "error"}). Please try again."
                    }
                    sessionStreamingTexts[targetSessionId] = message
                    if (sessionId.value == targetSessionId) streamingText.value = message
                }
            } finally {
                // 用户停止时不继续播放队列中未显示的文字。
                displayQueue.cancel()
                displayJob.cancel()
                val finalText = sessionStreamingTexts[targetSessionId].orEmpty()
                textMessageIndex?.let { index ->
                    updateSessionMessages(targetSessionId) { current ->
                        current.toMutableList().also { list ->
                            if (index in list.indices) list[index] = list[index].copy(text = finalText)
                        }
                    }
                }
                sessionStreamingTexts.remove(targetSessionId)
                streamJobs.remove(targetSessionId)
                respondingSessions -= targetSessionId
                streamingSessions -= targetSessionId
                persistSession(targetSessionId)
                refreshActiveSession(targetSessionId)
            }
        }
        streamJobs[targetSessionId] = job
        refreshActiveSession(targetSessionId)
    }

    /** 切换展示会话；不会取消其他会话的 SSE。 */
    fun selectSession(session: ChatSession) {
        sessionMessages[sessionId.value] = messages.value
        sessionTitles[sessionId.value] = customTitle.value   // 切走前记下它的标题(同上一行存 messages)
        val selectedMessages = sessionMessages.getOrPut(session.id) { session.messages }
        // 标题与 messages 同样"内存优先、磁盘兜底"(上一行的 getOrPut 就是这个语义)。
        // 不能直接用 session.title:重命名发生在流式期间时不会被立刻写盘 —— AskNovieScreen 的
        // 持久化 LaunchedEffect 带 `if (!isStreaming)` 守卫 —— 所以此刻磁盘上还是旧标题,
        // 直接采信它会把用户刚改的名字当场覆盖掉(切走再切回来就没了)。
        // 用 containsKey 而不是 getOrPut:值类型是 String?,而 getOrPut 把 null 当成"不存在",
        // 会把"用户特意清空了标题"这个状态错当成没记录、又退回磁盘上的旧标题。
        val selectedTitle =
            if (sessionTitles.containsKey(session.id)) sessionTitles[session.id] else session.title
        sessionId.value = session.id
        customTitle.value = selectedTitle
        sessionTitles[session.id] = selectedTitle
        messages.value = selectedMessages
        refreshActiveSession(session.id)
    }

    /** 开启新会话；当前会话若仍在流式回复，会继续在后台接收并保存。 */
    fun startNewSession() {
        sessionMessages[sessionId.value] = messages.value
        sessionTitles[sessionId.value] = customTitle.value   // 同 selectSession:切走前记下标题
        sessionId.value = UUID.randomUUID().toString()
        customTitle.value = null
        messages.value = emptyList()
        refreshActiveSession(sessionId.value)
    }

    /** 只停止当前正在展示的会话。 */
    fun stopStreamingReply() {
        val activeSessionId = sessionId.value
        streamJobs.remove(activeSessionId)?.cancel()
        respondingSessions -= activeSessionId
        streamingSessions -= activeSessionId
        persistSession(activeSessionId)
        refreshActiveSession(activeSessionId)
    }

    /** 删除当前会话并重置展示状态；删除操作不会被取消流的 finally 回写。 */
    fun deleteCurrentSession() {
        val deletedSessionId = sessionId.value
        streamJobs.remove(deletedSessionId)?.cancel()
        respondingSessions -= deletedSessionId
        streamingSessions -= deletedSessionId
        sessionMessages.remove(deletedSessionId)
        sessionTitles.remove(deletedSessionId)
        ChatSessionStore.delete(getApplication(), deletedSessionId)

        sessionId.value = UUID.randomUUID().toString()
        customTitle.value = null
        messages.value = emptyList()
        refreshActiveSession(sessionId.value)
    }

    private fun updateSessionMessages(
        targetSessionId: String,
        transform: (List<ChatMessage>) -> List<ChatMessage>,
    ) {
        val updated = transform(sessionMessages[targetSessionId].orEmpty())
        sessionMessages[targetSessionId] = updated
        if (sessionId.value == targetSessionId) messages.value = updated
    }

    private fun refreshActiveSession(changedSessionId: String) {
        if (sessionId.value != changedSessionId) return
        isResponding.value = changedSessionId in respondingSessions
        isStreaming.value = changedSessionId in streamingSessions
        streamingText.value = sessionStreamingTexts[changedSessionId].orEmpty()
    }

    private fun persistSession(targetSessionId: String) {
        val sessionMessages = sessionMessages[targetSessionId].orEmpty()
        if (sessionMessages.isEmpty()) return
        // 展示中的会话以 customTitle 为准(它才是最新的,包括刚刚在重命名对话框里改的);
        // 不在展示的会话取切走时留下的快照。
        //
        // 原来这里的 else 分支直接是 `null`,意味着**后台会话的流一结束,标题就被重置成第一句话**
        // —— 用户改的名字无声消失,而且 ChatSessionStore.upsert 是整条记录替换,改完就找不回来了。
        // 而后台流是这个类明确支持的用法(见类 KDoc:切换历史不会停掉旧会话的回复),所以这不是
        // 极端竞态,是正常操作路径:重命名 A → 在 A 发消息 → 切到 B → A 的流结束 → A 的名字没了。
        val title = if (sessionId.value == targetSessionId) {
            customTitle.value
        } else {
            sessionTitles[targetSessionId]
        }?.takeIf { it.isNotBlank() }
            ?: sessionMessages.first().text.trim().takeIf { it.isNotEmpty() }
            ?: sessionMessages.first().attachments.firstOrNull()?.name
            ?: "New chat"
        ChatSessionStore.upsert(
            getApplication(),
            ChatSession(targetSessionId, title, System.currentTimeMillis(), sessionMessages),
        )
    }
}

/** 按 Unicode code point 分块，避免将 emoji 拆成半个 surrogate。 */
private fun String.displayChunks(chunkSize: Int): List<String> {
    if (isEmpty()) return emptyList()
    val chunks = ArrayList<String>((length + chunkSize - 1) / chunkSize)
    var chunkStart = 0
    var index = 0
    var codePointCount = 0
    while (index < length) {
        index += Character.charCount(codePointAt(index))
        codePointCount++
        if (codePointCount == chunkSize) {
            chunks += substring(chunkStart, index)
            chunkStart = index
            codePointCount = 0
        }
    }
    if (chunkStart < length) chunks += substring(chunkStart)
    return chunks
}
