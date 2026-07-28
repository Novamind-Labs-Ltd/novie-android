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
import kotlinx.coroutines.launch

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
                            // 到达即追加，不再做人工打字节流。
                            //
                            // 原来是每 32ms 画 2 个 code point。因为 collect 是顺序的，每个
                            // delay 都挂起收集方、经 flowOn 的 64 槽缓冲一路反压到
                            // readUtf8Line —— 相当于用展示时钟给 socket 限速到 62.5 字/秒，
                            // 而模型出 token 通常快得多，于是缓冲填满后每一帧都在等动画。
                            // 一个 1200 字的回答服务端 4 秒发完，屏幕上要爬 19 秒。这正是
                            // 这个 PR 想消灭的「等很久」，只是从队首挪到了全程。
                            //
                            // 而且它换不来平滑：AssistantMessageContent 已经用
                            // STREAMING_MARKDOWN_FRAME_MS(120ms) 独立节流 Markdown 重绘，
                            // 比这里的 32ms 粗得多 —— 五次 delay 里约四次根本不产生任何一帧，
                            // 纯粹在给网络限速。平滑由下游负责，这里只管把数据交出去。
                            updateSessionMessages(targetSessionId) { current ->
                                current.toMutableList().also { list ->
                                    list[list.lastIndex] = list.last().copy(
                                        text = list.last().text + event.delta,
                                    )
                                }
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

