package com.novamind.app.feature.asknovie

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.feature.asknovie.data.AskNovieChat
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

    var seeded = false
        private set

    fun markSeeded() {
        seeded = true
        sessionMessages[sessionId.value] = messages.value
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
                            updateSessionMessages(targetSessionId) { current ->
                                current.toMutableList().also { list ->
                                    list[list.lastIndex] = list.last().copy(text = list.last().text + event.delta)
                                }
                            }
                        }
                        is ChatStreamEvent.Failure -> {
                            ensureBubble()
                            val message = event.message
                                ?: "Something went wrong (${event.code ?: "error"}). Please try again."
                            updateSessionMessages(targetSessionId) { current ->
                                current.toMutableList().also { list ->
                                    list[list.lastIndex] = list.last().copy(text = message)
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
