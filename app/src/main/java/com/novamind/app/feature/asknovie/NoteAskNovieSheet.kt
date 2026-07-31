package com.novamind.app.feature.asknovie

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animate
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import com.novamind.app.R
import com.novamind.app.common.audio.AudioRecordingFormat
import com.novamind.app.common.config.AppConfig
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.feature.asknovie.components.AssistantText
import com.novamind.app.feature.asknovie.components.Bg
import com.novamind.app.feature.asknovie.components.ComposerRoundButton
import com.novamind.app.feature.asknovie.components.Hint
import com.novamind.app.feature.asknovie.components.SendButton
import com.novamind.app.feature.asknovie.components.TextTitle
import com.novamind.app.feature.asknovie.components.TypingIndicator
import com.novamind.app.feature.asknovie.components.UserBubble
import com.novamind.app.feature.asknovie.data.AskNovieChat
import com.novamind.app.feature.asknovie.data.AskNovieTranscriptionRepository
import com.novamind.app.feature.asknovie.data.ChatStreamEvent
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.components.RecordingUploadOutcome
import com.novamind.app.ui.components.VoiceRecordingBar
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.PermissionUtils
import com.novamind.app.util.ToastUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private const val NOTE_ASK_SHEET_HEIGHT_FRACTION = 0.86f

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
                .firstOrNull { it.id == conversationId }?.messages.orEmpty(),
        )
    }
    var streamingText by remember { mutableStateOf("") }
    var responding by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 录音不依赖通知授权结果；只影响前台录音通知是否可见。 */ }
    val ensureNotificationPermission = {
        if (PermissionUtils.needsNotificationPermission(context)) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val recordPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            ensureNotificationPermission()
            isRecording = true
        } else {
            ToastUtils.short(context, "Microphone permission is required to record audio")
        }
    }

    fun send() {
        val question = input.trim()
        if (question.isEmpty() || responding) return
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
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
                    noteId = noteId,
                ).collect { event ->
                    when (event) {
                        is ChatStreamEvent.TextDelta -> streamingText += event.delta
                        is ChatStreamEvent.Failure -> failure =
                            event.message ?: "Something went wrong. Please try again."

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

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val sheetHeightPx = with(density) {
        configuration.screenHeightDp.dp.toPx() * NOTE_ASK_SHEET_HEIGHT_FRACTION
    }
    val dismissThresholdPx = sheetHeightPx / 4f
    val maxPullDistancePx = sheetHeightPx
    var pullOffsetPx by remember { mutableFloatStateOf(0f) }
    val pullToDismissConnection = remember(listState, sheetState, dismissThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f || pullOffsetPx <= 0f) {
                    return Offset.Zero
                }
                val previous = pullOffsetPx
                pullOffsetPx = (pullOffsetPx + available.y).coerceAtLeast(0f)
                return Offset(x = 0f, y = pullOffsetPx - previous)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (
                    source != NestedScrollSource.UserInput ||
                    available.y <= 0f ||
                    listState.canScrollBackward
                ) {
                    return Offset.Zero
                }
                // 下拉距离越长阻尼越明显，保持跟手但避免轻微手势造成大幅位移。
                val resistance = (1f - pullOffsetPx / maxPullDistancePx).coerceIn(0.25f, 0.7f)
                pullOffsetPx = (pullOffsetPx + available.y * resistance)
                    .coerceAtMost(maxPullDistancePx)
                return Offset(x = 0f, y = available.y)
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                // fling 只负责把消息列表送到顶部；关闭必须由用户继续主动下拉到 1/4 高度。
                val shouldDismiss = pullOffsetPx >= dismissThresholdPx
                if (shouldDismiss) {
                    // 保留手势产生的位移直到 Material 隐藏动画结束，避免弹窗先跳回展开位置闪一帧。
                    sheetState.hide()
                    pullOffsetPx = 0f
                    latestOnDismiss()
                } else if (pullOffsetPx > 0f) {
                    animate(
                        initialValue = pullOffsetPx,
                        targetValue = 0f,
                    ) { value, _ ->
                        pullOffsetPx = value
                    }
                }
                return available
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.graphicsLayer { translationY = pullOffsetPx },
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        containerColor = Bg,
        scrimColor = Color.Black.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(NOTE_ASK_SHEET_HEIGHT_FRACTION)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "Ask for ${noteTitle.ifBlank { "this note" }}…",
                    modifier = Modifier.weight(1f),
                    color = TextTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Close",
                        tint = TextTitle,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(pullToDismissConnection),
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

            if (isRecording) {
                VoiceRecordingBar(
                    onCancel = { isRecording = false },
                    onConfirm = { path, _ ->
                        isRecording = false
                        runCatching { java.io.File(path).delete() }
                        if (input.isBlank()) {
                            ToastUtils.short(
                                context,
                                "We couldn't hear any speech. Please try again.",
                            )
                        } else {
                            send()
                        }
                    },
                    onUpload = { path, durationSeconds ->
                        when {
                            durationSeconds > AppConfig.AskNovie.MAX_VOICE_SECONDS -> {
                                ToastUtils.short(context, "Voice input can be up to 60 seconds.")
                                RecordingUploadOutcome.DiscardFailure
                            }

                            !java.io.File(path).isFile -> {
                                ToastUtils.short(
                                    context,
                                    "The recording is unavailable. Please record again.",
                                )
                                RecordingUploadOutcome.DiscardFailure
                            }

                            else -> when (val result = AskNovieTranscriptionRepository.transcribe(
                                path,
                                durationSeconds,
                            )) {
                                is ApiResult.Success -> {
                                    val transcription = result.data
                                    if (transcription?.text.isNullOrBlank()) {
                                        ToastUtils.short(
                                            context,
                                            "We couldn't hear any speech. Please try again.",
                                        )
                                        RecordingUploadOutcome.DiscardFailure
                                    } else {
                                        val baseInput = input.trimEnd()
                                        transcription.partialTexts.ifEmpty { listOf(transcription.text) }
                                            .forEachIndexed { index, partialText ->
                                                input = listOf(
                                                    baseInput,
                                                    partialText
                                                ).filter { it.isNotBlank() }.joinToString(" ")
                                                if (index < transcription.partialTexts.lastIndex) {
                                                    delay(
                                                        AppConfig.AskNovie.VOICE_TRANSCRIPTION_STEP_DELAY_MS,
                                                    )
                                                }
                                            }
                                        input = listOf(
                                            baseInput,
                                            transcription.text
                                        ).filter { it.isNotBlank() }.joinToString(" ")
                                        RecordingUploadOutcome.Success
                                    }
                                }

                                is ApiResult.BizError -> {
                                    ToastUtils.short(
                                        context,
                                        result.message ?: "Couldn't transcribe the recording.",
                                    )
                                    if (result.httpStatus >= 500 || result.httpStatus == 408 || result.httpStatus == 429) {
                                        RecordingUploadOutcome.RetryableFailure
                                    } else {
                                        RecordingUploadOutcome.DiscardFailure
                                    }
                                }

                                is ApiResult.NetworkError -> {
                                    ToastUtils.short(
                                        context,
                                        "Couldn't transcribe the recording. Please retry.",
                                    )
                                    RecordingUploadOutcome.RetryableFailure
                                }
                            }
                        }
                    },
                    compact = true,
                    autoStart = true,
                    sendingLabel = "Transcribing…",
                    recordingFormat = AudioRecordingFormat.M4A,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                NoteAskComposer(
                    input = input,
                    onInputChange = { input = it },
                    responding = responding,
                    onVoice = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        if (PermissionUtils.hasAudioPermission(context)) {
                            ensureNotificationPermission()
                            isRecording = true
                        } else {
                            recordPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onSend = ::send,
                )
            }
        }
    }
}

@Composable
private fun NoteAskComposer(
    input: String,
    onInputChange: (String) -> Unit,
    responding: Boolean,
    onVoice: () -> Unit,
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
                    ComposerRoundButton(R.drawable.ic_mic, "Voice", onClick = onVoice)
                    SendButton(enabled = input.isNotBlank() && !responding, onClick = onSend)
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Note Ask Novie · Composer")
@Composable
private fun NoteAskNovieComposerPreview() {
    AppTheme {
        NoteAskComposer(
            input = "",
            onInputChange = {},
            responding = false,
            onVoice = {},
            onSend = {},
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 760,
    name = "Note Ask Novie · Conversation",
)
@Composable
private fun NoteAskNovieSheetPreview() {
    val previewMessages = listOf(
        ChatMessage(
            role = Role.User,
            text = "What are the most important follow-up actions?",
        ),
        ChatMessage(
            role = Role.Assistant,
            text = "The key follow-ups are to confirm the guest list, assign the event devices, " + "and send the calendar invitation before Friday.",
        ),
        ChatMessage(
            role = Role.User,
            text = "Turn that into a short checklist.",
        ),
    )

    AppTheme {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
            color = Bg,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "Ask for Q3 marketing campaign…",
                        modifier = Modifier.weight(1f),
                        color = TextTitle,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                    )
                    IconButton(
                        onClick = {},
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Close",
                            tint = TextTitle,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(previewMessages) { message ->
                        when (message.role) {
                            Role.User -> UserBubble(message)
                            Role.Assistant -> AssistantText(message.text)
                        }
                    }
                    item {
                        AssistantText(
                            text = "1. Confirm the guest list\n2. Assign devices\n3. Send invites",
                            isTyping = true,
                        )
                    }
                }

                NoteAskComposer(
                    input = "",
                    onInputChange = {},
                    responding = true,
                    onVoice = {},
                    onSend = {},
                )
            }
        }
    }
}
