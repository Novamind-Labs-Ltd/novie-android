package com.novamind.app.feature.asknovie

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.asknovie.components.AssistantText
import com.novamind.app.feature.asknovie.components.Bg
import com.novamind.app.feature.asknovie.components.ComposerRoundButton
import com.novamind.app.feature.asknovie.components.Hint
import com.novamind.app.feature.asknovie.components.SendButton
import com.novamind.app.feature.asknovie.components.TextTitle
import com.novamind.app.feature.asknovie.components.TypingIndicator
import com.novamind.app.feature.asknovie.components.UserBubble
import com.novamind.app.feature.asknovie.data.AskNovieChat
import com.novamind.app.feature.asknovie.data.ChatStreamEvent
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import java.util.UUID
import kotlinx.coroutines.launch

/** 编辑页内基于当前笔记上下文的 Ask Novie 对话弹层（Figma 1656:36837）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteAskNovieSheet(
    noteId: String,
    noteTitle: String,
    noteBody: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // 同一笔记始终映射为同一个合法 UUID；应用重启或重新打开页面后也不会新建会话。
    val conversationId = remember(noteId) {
        UUID.nameUUIDFromBytes("note:$noteId".encodeToByteArray()).toString()
    }
    var input by remember { mutableStateOf("") }
    var messages by remember(conversationId) {
        mutableStateOf(
            ChatSessionStore.load(context)
                .firstOrNull { it.id == conversationId }
                ?.messages
                .orEmpty(),
        )
    }
    var streamingText by remember { mutableStateOf("") }
    var responding by remember { mutableStateOf(false) }

    fun send() {
        val question = input.trim()
        if (question.isEmpty() || responding) return
        keyboardController?.hide()
        focusManager.clearFocus()
        input = ""
        messages = messages + ChatMessage(Role.User, question)
        responding = true
        scope.launch {
            val request = if (messages.count { it.role == Role.User } == 1) {
                buildString {
                    appendLine("Use the following note as context. Answer the user's question about it.")
                    appendLine("Note title: $noteTitle")
                    appendLine("Note content:")
                    appendLine(noteBody)
                    appendLine("User question: $question")
                }
            } else {
                question
            }
            var failure: String? = null
            try {
                AskNovieChat.streamChat(
                    mode = "chat",
                    conversationId = conversationId,
                    input = request,
                ).collect { event ->
                    when (event) {
                        is ChatStreamEvent.TextDelta -> streamingText += event.delta
                        is ChatStreamEvent.Failure -> failure = event.message ?: "Something went wrong. Please try again."
                        is ChatStreamEvent.Card,
                        is ChatStreamEvent.Status,
                        is ChatStreamEvent.Done,
                        -> Unit
                    }
                }
            } finally {
                val answer = streamingText.ifBlank { failure.orEmpty() }
                if (answer.isNotBlank()) messages = messages + ChatMessage(Role.Assistant, answer)
                if (messages.isNotEmpty()) {
                    ChatSessionStore.upsert(
                        context,
                        ChatSession(
                            id = conversationId,
                            title = noteTitle.ifBlank { "Note conversation" },
                            updatedAt = System.currentTimeMillis(),
                            messages = messages,
                        ),
                    )
                }
                streamingText = ""
                responding = false
            }
        }
    }

    LaunchedEffect(messages.size, streamingText) {
        val itemCount = messages.size + if (responding || streamingText.isNotEmpty()) 1 else 0
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        containerColor = Bg,
        scrimColor = Color.Black.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "Ask for ${noteTitle.ifBlank { "this note" }}…",
                    modifier = Modifier.weight(1f),
                    color = TextTitle,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Close",
                        tint = TextTitle,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(messages) { message ->
                    when (message.role) {
                        Role.User -> UserBubble(message)
                        Role.Assistant -> AssistantText(message.text)
                    }
                }
                if (responding || streamingText.isNotEmpty()) {
                    item {
                        if (streamingText.isEmpty()) TypingIndicator()
                        else AssistantText(streamingText, isTyping = true)
                    }
                }
            }

            NoteAskComposer(
                input = input,
                onInputChange = { input = it },
                responding = responding,
                onSend = ::send,
            )
        }
    }
}

@Composable
private fun NoteAskComposer(
    input: String,
    onInputChange: (String) -> Unit,
    responding: Boolean,
    onSend: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = BackgroundColors.Surface.default.current(),
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = TextTitle, fontSize = 16.sp, lineHeight = 24.sp),
                cursorBrush = SolidColor(TextTitle),
                minLines = 1,
                maxLines = 5,
                decorationBox = { inner ->
                    if (input.isEmpty()) Text("Message with Novie", color = Hint, fontSize = 16.sp)
                    inner()
                },
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ComposerRoundButton(R.drawable.ic_add, "Add", iconSize = 16.dp, onClick = {})
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ComposerRoundButton(R.drawable.ic_mic, "Voice", onClick = {})
                    SendButton(enabled = input.isNotBlank() && !responding, onClick = onSend)
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 760)
@Composable
private fun NoteAskNovieSheetPreview() {
    AppTheme {
        NoteAskComposer(input = "", onInputChange = {}, responding = false, onSend = {})
    }
}
