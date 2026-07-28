package com.novamind.app.feature.asknovie

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.feature.asknovie.data.AskNovieChat
import com.novamind.app.feature.asknovie.data.AskNovieTranscriptionRepository
import com.novamind.app.feature.asknovie.data.AskNovieTranscriptionRepository.VoiceTranscription
import com.novamind.app.feature.asknovie.data.ChatStreamEvent
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DISPLAY_CHUNK_SIZE = 4
private const val DISPLAY_INTERVAL_MS = 64L

/**
 * Ask Novie 的 Activity 级会话状态。
 *
 * 每个会话拥有独立的 SSE Job 和消息快照：切换历史只切换正在展示的会话，
 * 不会停止旧会话的服务端回复；旧流结束后仍会写回对应的历史记录。
 */
class AskNovieChatViewModel(application: Application) : AndroidViewModel(application) {
    val messages = mutableStateOf<List<ChatMessage>>(emptyList())
    val streamingText = mutableStateOf("")
    val isResponding = mutableStateOf(false)
    val isStreaming = mutableStateOf(false)
    val responseJob = mutableStateOf<Job?>(null)
    val sessionId = mutableStateOf(UUID.randomUUID().toString())
    val customTitle = mutableStateOf<String?>(null)

    private val sessionMessages = mutableMapOf<String, List<ChatMessage>>()
    private val streamJobs = mutableMapOf<String, Job>()
    private val respondingSessions = mutableSetOf<String>()
    private val streamingSessions = mutableSetOf<String>()
    private val sessionStreamingTexts = mutableMapOf<String, String>()
    private var handledNewSessionRequestId = 0L

    var seeded = false
        private set

    fun markSeeded() {
        seeded = true
        sessionMessages[sessionId.value] = messages.value
    }

    /** 上传 Ask Novie 短语音并同步取回转写文字，不自动发送消息。 */
    suspend fun transcribeVoice(path: String, durationSeconds: Int): ApiResult<VoiceTranscription> =
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
            var streamFailure: ChatStreamEvent.Failure? = null

            fun ensureBubble() {
                if (!appended) {
                    respondingSessions -= targetSessionId
                    streamingSessions += targetSessionId
                    updateSessionMessages(targetSessionId) { it + ChatMessage(Role.Assistant, "") }
                    appended = true
                    refreshActiveSession(targetSessionId)
                }
            }

            // 网络收帧与 UI 打字机解耦：SSE collector 只入队，不被显示节奏反向阻塞。
            // UNLIMITED 保证后端突发输出时不丢 delta；单消费者严格保持原始顺序。
            val deltaQueue = Channel<String>(capacity = Channel.UNLIMITED)
            val displayJob = launch {
                for (delta in deltaQueue) {
                    ensureBubble()
                    delta.displayChunks(DISPLAY_CHUNK_SIZE).forEach { chunk ->
                        val updatedText = sessionStreamingTexts[targetSessionId].orEmpty() + chunk
                        sessionStreamingTexts[targetSessionId] = updatedText
                        if (sessionId.value == targetSessionId) {
                            streamingText.value = updatedText
                        }
                        delay(DISPLAY_INTERVAL_MS)
                    }
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
                            deltaQueue.send(event.delta)
                        }
                        is ChatStreamEvent.Failure -> {
                            streamFailure = event
                        }
                        is ChatStreamEvent.Card,
                        is ChatStreamEvent.Status,
                        is ChatStreamEvent.Done,
                        -> Unit
                    }
                }
                // 正常结束时先把队列中已收到的文字播完，再收束 streaming 状态。
                deltaQueue.close()
                displayJob.join()

                streamFailure?.let { failure ->
                    ensureBubble()
                    val message = failure.message
                        ?: "Something went wrong (${failure.code ?: "error"}). Please try again."
                    sessionStreamingTexts[targetSessionId] = message
                    if (sessionId.value == targetSessionId) streamingText.value = message
                }
            } finally {
                // 用户停止时不继续播放队列中未显示的文字。
                deltaQueue.cancel()
                displayJob.cancel()
                val finalText = sessionStreamingTexts[targetSessionId].orEmpty()
                if (appended) {
                    updateSessionMessages(targetSessionId) { current ->
                        current.toMutableList().also { list ->
                            list[list.lastIndex] = list.last().copy(text = finalText)
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
        streamingText.value = sessionStreamingTexts[changedSessionId].orEmpty()
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
