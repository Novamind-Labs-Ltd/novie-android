package com.novamind.app.feature.asknovie

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.common.net.TranscribeData
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.feature.asknovie.data.AskNovieChat
import com.novamind.app.feature.asknovie.data.AskNovieTranscriptionRepository
import com.novamind.app.feature.asknovie.data.ChatStreamEvent
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DISPLAY_CHUNK_SIZE = 2
private const val DISPLAY_INTERVAL_MS = 32L

/**
 * 单个 delta 最多分成多少帧画完（每帧 [DISPLAY_INTERVAL_MS]）。
 *
 * 上限存在的理由是反压而不是观感：`collect` 是顺序的，打字动画的每个 `delay` 都会挂起
 * 收集方，经 `flowOn` 的缓冲一路反压到 `readUtf8Line`。没有上限时，chunk 固定 2 字，
 * 一个 500 字的 delta 要 250 帧 ≈ 8 秒，这 8 秒里 SSE 根本没人读。
 * 16 帧 ≈ 0.5s 封顶。逐 token 的小 delta 落不到这个分支，打字手感原样不变。
 */
private const val MAX_FRAMES_PER_DELTA = 16

/**
 * Ask Novie 的 Activity 级会话状态。
 *
 * 每个会话拥有独立的 SSE Job 和消息快照：切换历史只切换正在展示的会话，
 * 不会停止旧会话的服务端回复；旧流结束后仍会写回对应的历史记录。
 */
class AskNovieChatViewModel(application: Application) : AndroidViewModel(application) {
    val messages = mutableStateOf<List<ChatMessage>>(emptyList())
    val isResponding = mutableStateOf(false)
    val isStreaming = mutableStateOf(false)
    val responseJob = mutableStateOf<Job?>(null)
    val sessionId = mutableStateOf(UUID.randomUUID().toString())
    val customTitle = mutableStateOf<String?>(null)

    private val sessionMessages = mutableMapOf<String, List<ChatMessage>>()
    private val streamJobs = mutableMapOf<String, Job>()
    private val respondingSessions = mutableSetOf<String>()
    private val streamingSessions = mutableSetOf<String>()
    private var handledNewSessionRequestId = 0L

    var seeded = false
        private set

    fun markSeeded() {
        seeded = true
        sessionMessages[sessionId.value] = messages.value
    }

    /** 上传 Ask Novie 短语音并同步取回转写文字，不自动发送消息。 */
    suspend fun transcribeVoice(path: String, durationSeconds: Int): ApiResult<TranscribeData> =
        AskNovieTranscriptionRepository.transcribe(path, durationSeconds)

    /** 同一个首页进入事件只消费一次，避免页面重新进入组合时重复创建空会话。 */
    fun consumeNewSessionRequest(requestId: Long): Boolean {
        if (requestId <= 0L || requestId == handledNewSessionRequestId) return false
        handledNewSessionRequestId = requestId
        return true
    }

    /** 开始当前会话的 SSE；其他历史会话的在途回复不受影响。 */
    fun startStreamingReply(prompt: String) {
        val targetSessionId = sessionId.value
        if (streamJobs.containsKey(targetSessionId)) return

        sessionMessages[targetSessionId] = messages.value
        respondingSessions += targetSessionId
        refreshActiveSession(targetSessionId)

        val job = viewModelScope.launch {
            var appended = false

            fun ensureBubble() {
                if (!appended) {
                    respondingSessions -= targetSessionId
                    streamingSessions += targetSessionId
                    updateSessionMessages(targetSessionId) { it + ChatMessage(Role.Assistant, "") }
                    appended = true
                    refreshActiveSession(targetSessionId)
                }
            }

            try {
                AskNovieChat.streamChat(
                    mode = "chat",
                    conversationId = targetSessionId,
                    input = prompt,
                ).collect { event ->
                    when (event) {
                        is ChatStreamEvent.TextDelta -> {
                            ensureBubble()
                            // SSE 可能一次带回整段文本；展示层按固定节奏追加，保持打字效果。
                            // chunk 按本次 delta 的长度放大，使**任何**一个 delta 都在
                            // MAX_FRAMES_PER_DELTA 帧内画完。collect 是顺序的，这里每个
                            // delay 都会挂起收集方、经 flowOn 的缓冲反压到 readUtf8Line：
                            // 固定 2 字/帧时，一个 500 字的 delta 会把 SSE 读阻塞 8 秒，
                            // 后续帧只能干等——正好抵消掉这个 PR 争取到的流式。
                            // 小 delta（逐 token 的常态）走 DISPLAY_CHUNK_SIZE，打字手感不变。
                            val chunkSize = maxOf(
                                DISPLAY_CHUNK_SIZE,
                                (event.delta.length + MAX_FRAMES_PER_DELTA - 1) / MAX_FRAMES_PER_DELTA,
                            )
                            event.delta.displayChunks(chunkSize).forEach { chunk ->
                                updateSessionMessages(targetSessionId) { current ->
                                    current.toMutableList().also { list ->
                                        list[list.lastIndex] = list.last().copy(
                                            text = list.last().text + chunk,
                                        )
                                    }
                                }
                                delay(DISPLAY_INTERVAL_MS)
                            }
                        }
                        is ChatStreamEvent.Failure -> {
                            ensureBubble()
                            val message = event.message
                                ?: "Something went wrong (${event.code ?: "error"}). Please try again."
                            updateSessionMessages(targetSessionId) { current ->
                                current.toMutableList().also { list ->
                                    // 追加,不是覆盖。流到一半才失败时(读超时/掉网),之前已经
                                    // 画出来的半截回答是有价值的,不能被一句错误提示抹掉——那对
                                    // 用户等于"答案凭空消失了"。开流前就失败时气泡本来是空的,
                                    // 追加与覆盖等价,所以不需要分两种情况。
                                    val shown = list.last().text
                                    list[list.lastIndex] = list.last().copy(
                                        text = if (shown.isEmpty()) message else "$shown\n\n$message",
                                    )
                                }
                            }
                        }
                        is ChatStreamEvent.Card,
                        is ChatStreamEvent.Status,
                        is ChatStreamEvent.Done,
                        -> Unit
                    }
                }
            } finally {
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
        val selectedMessages = sessionMessages.getOrPut(session.id) { session.messages }
        sessionId.value = session.id
        customTitle.value = session.title
        messages.value = selectedMessages
        refreshActiveSession(session.id)
    }

    /** 开启新会话；当前会话若仍在流式回复，会继续在后台接收并保存。 */
    fun startNewSession() {
        sessionMessages[sessionId.value] = messages.value
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
        responseJob.value = streamJobs[changedSessionId]
    }

    private fun persistSession(targetSessionId: String) {
        val sessionMessages = sessionMessages[targetSessionId].orEmpty()
        if (sessionMessages.isEmpty()) return
        val title = if (sessionId.value == targetSessionId) {
            customTitle.value
        } else {
            null
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

/** 按 Unicode code point 分块，避免在动画过程中把 emoji 拆成半个 surrogate。 */
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
