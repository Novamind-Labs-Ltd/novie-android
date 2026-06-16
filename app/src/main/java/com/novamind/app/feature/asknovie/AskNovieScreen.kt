package com.novamind.app.feature.asknovie

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.components.VoiceRecordingBar
import kotlinx.coroutines.launch

private val Bg = Color(0xFFF1EEE6)
private val Card = Color(0xFFFFFFFF)
private val TextTitle = Color(0xFF1A1A1A)
private val TextSub = Color(0xFF6B6B6B)
private val Dark = Color(0xFF1A1A1A)
private val ChipText = Color(0xFF3A3A3A)
private val SendGreen = Color(0xFF2E9E5B)
private val MenuBg = Color(0xFFF4F2EA)

/** 预设的快捷建议（点击填入输入框）。 */
private val suggestions = listOf(
    "Help me brainstorm",
    "Who have I promised to follow up",
    "Summarize my notes",
)

/** 一条对话消息。 */
private enum class Role { User, Assistant }
private data class ChatMessage(val role: Role, val text: String)

/** 临时 mock 回复（后续替换为真实接口）。 */
private fun mockReply(prompt: String): String {
    val p = prompt.lowercase()
    val offTopic = listOf("movie", "cinema", "weather", "news", "stock", "score", "lottery", "电影", "天气")
    if (offTopic.any { p.contains(it) }) {
        return "That’s a bit outside my current scope. I’m best at helping with " +
            "project management, strategic planning, brainstorming, and creative tasks. " +
            "Is there something in those areas I can help you with instead?"
    }
    return "Here’s a quick take on “${prompt.trim()}”. " +
        "(This is a mock reply for now — I’ll connect to the real assistant later.) " +
        "Want me to break it into next steps?"
}

/**
 * Ask Novie 聊天入口页（空状态）。匹配设计稿：
 * 顶部返回/历史/更多，中部问候，底部快捷建议 + 输入框（含 + 与麦克风）。
 *
 * @param userName 问候语显示的名字
 * @param onBack 返回上一页
 * @param onSend 发送消息（当前留空，后续可接入对话流）
 */
@Composable
fun AskNovieScreen(
    userName: String = "Jerry",
    onBack: () -> Unit = {},
    onSend: (String) -> Unit = {},
    onShare: () -> Unit = {},
    onRename: () -> Unit = {},
    onExportToNotes: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    // 点麦克风后进入录音状态
    var isRecording by remember { mutableStateOf(false) }
    // 右上角「更多」菜单显隐
    var showMoreMenu by remember { mutableStateOf(false) }
    // 聊天历史弹窗显隐
    var showHistory by remember { mutableStateOf(false) }
    // 对话消息列表 + 助手是否正在回复
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isResponding by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val context = LocalContext.current

    // 语音播报（Android TTS），随页面生命周期创建与释放
    val tts = remember {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) engine.language = java.util.Locale.getDefault()
        }
        engine
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { tts.stop(); tts.shutdown() }
    }
    val speak: (String) -> Unit = { text ->
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "novie-tts")
    }

    // 发送：追加用户消息 → mock 回复（模拟思考延迟）
    val send: (String) -> Unit = { raw ->
        val text = raw.trim()
        if (text.isNotEmpty() && !isResponding) {
            messages = messages + ChatMessage(Role.User, text)
            input = ""
            isResponding = true
            onSend(text)
            scope.launch {
                kotlinx.coroutines.delay(700)
                messages = messages + ChatMessage(Role.Assistant, mockReply(text))
                isResponding = false
            }
        }
    }

    // 新消息时滚到底部
    androidx.compose.runtime.LaunchedEffect(messages.size, isResponding) {
        val count = messages.size + if (isResponding) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    // 录音时系统返回先退出录音
    androidx.activity.compose.BackHandler(enabled = isRecording) { isRecording = false }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Bg)
            // 点击输入框以外的空白区域 → 清焦点收起键盘（子组件各自消费点击不受影响）
            .pointerInput(Unit) {
                detectTapGestures { focusManager.clearFocus() }
            },
    ) {
        // ── 顶部栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(R.drawable.ic_arrow_back, "返回", onClick = onBack)
            Spacer(Modifier.weight(1f))
            // 历史 + 更多 合并胶囊
            Surface(color = Card, shape = RoundedCornerShape(50), shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BareIconButton(R.drawable.ic_history, "历史", onClick = { showHistory = true })
                    Box {
                        BareIconButton(R.drawable.ic_more, "更多", onClick = { showMoreMenu = true })
                        MoreMenu(
                            expanded = showMoreMenu,
                            onDismiss = { showMoreMenu = false },
                            onShare = { showMoreMenu = false; onShare() },
                            onRename = { showMoreMenu = false; onRename() },
                            onExportToNotes = { showMoreMenu = false; onExportToNotes() },
                            onDelete = { showMoreMenu = false; onDelete() },
                        )
                    }
                }
            }
        }

        // ── 中部：空状态问候 / 对话列表 ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            if (messages.isEmpty() && !isResponding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Hi, $userName",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextTitle,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "What’s on your mind?",
                        fontSize = 15.sp,
                        color = TextSub,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(messages) { msg ->
                        if (msg.role == Role.User) UserBubble(msg.text)
                        else AssistantText(msg.text, onSpeak = speak)
                    }
                    if (isResponding) {
                        item { TypingIndicator() }
                    }
                }
            }
        }

        // ── 底部：录音条 / 快捷建议 + 输入框 ──
        if (isRecording) {
            VoiceRecordingBar(
                onCancel = { isRecording = false },
                onConfirm = { _ ->
                    // TODO: 保存录音并发送（需 MediaRecorder + RECORD_AUDIO 权限）
                    isRecording = false
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
        ) {
            // 建议 chips（横向滚动）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                suggestions.forEach { s ->
                    SuggestionChip(text = s, onClick = { input = s })
                }
            }

            Spacer(Modifier.height(12.dp))

            // 输入框
            Surface(color = Card, shape = RoundedCornerShape(28.dp), shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BareIconButton(R.drawable.ic_add, "添加")

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (input.isEmpty()) {
                            Text("Message with Novie", color = TextSub, fontSize = 15.sp)
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            textStyle = TextStyle(color = TextTitle, fontSize = 15.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Dark),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send(input) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // 右侧按钮：有内容 → 绿色发送；无内容 → 语音
                    if (input.isNotBlank()) {
                        SendButton(onClick = { send(input) })
                    } else {
                        MicButton(
                            onClick = {
                                // 点麦克风 → 收键盘并弹出录音条
                                keyboardController?.hide()
                                isRecording = true
                            },
                        )
                    }
                }
            }
        }
        }
    }

    // 聊天历史底部弹窗
    if (showHistory) {
        ChatHistorySheet(
            onDismiss = { showHistory = false },
            onNewChat = {
                showHistory = false
                messages = emptyList()
                input = ""
            },
            onSelectChat = { showHistory = false },
        )
    }
}

@Composable
private fun CircleIconButton(iconRes: Int, desc: String, onClick: () -> Unit) {
    Surface(color = Card, shape = CircleShape, shadowElevation = 1.dp) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(iconRes),
                contentDescription = desc,
                tint = TextTitle,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun BareIconButton(iconRes: Int, desc: String, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(iconRes),
            contentDescription = desc,
            tint = TextTitle,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Surface(color = Card, shape = RoundedCornerShape(50), shadowElevation = 1.dp) {
        Text(
            text = text,
            color = ChipText,
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** 发送按钮：绿色圆形，仅在有输入内容时显示。 */
@Composable
private fun SendButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(SendGreen)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, color = Color.White),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_arrow_up),
            contentDescription = "发送",
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 语音按钮（深色圆形）：失焦时显示。 */
@Composable
private fun MicButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Dark)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, color = Color.White),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_mic),
            contentDescription = "语音",
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 右上角「更多」下拉菜单。 */
@Composable
private fun MoreMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onExportToNotes: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = MenuBg,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 12.dp,
        tonalElevation = 0.dp,
        modifier = Modifier.width(220.dp),
    ) {
        MoreMenuItem("Share", onShare)
        MoreMenuItem("Rename", onRename)
        MoreMenuItem("Export to notes", onExportToNotes)
        MoreMenuItem("Delete", onDelete)
    }
}

@Composable
private fun MoreMenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = TextTitle, fontSize = 16.sp) },
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 24.dp,
            vertical = 12.dp,
        ),
    )
}

/** 用户消息气泡：右对齐，浅色圆角。 */
@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = Card,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 1.dp,
            modifier = Modifier.padding(start = 36.dp),
        ) {
            SelectionContainer {
                Text(
                    text = text,
                    color = TextTitle,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** 助手消息：纯文本（可选中复制）+ 操作行（复制/分享/翻译/语音播报）。 */
@Composable
private fun AssistantText(text: String, onSpeak: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                text = text,
                color = TextTitle,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        AssistantActions(text = text, onSpeak = onSpeak)
    }
}

/** 助手回复下方的操作行：复制 / 分享 / 翻译 / 语音播报。 */
@Composable
private fun AssistantActions(text: String, onSpeak: (String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        ActionIcon(R.drawable.ic_copy, "复制") {
            clipboard.setText(AnnotatedString(text))
            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
        }
        ActionIcon(R.drawable.ic_share, "分享") {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, "分享"))
        }
        ActionIcon(R.drawable.ic_translate, "翻译") {
            // TODO: 接入翻译服务（如 ML Kit / 翻译 API）
            Toast.makeText(context, "翻译功能即将上线", Toast.LENGTH_SHORT).show()
        }
        ActionIcon(R.drawable.ic_volume, "语音播报") { onSpeak(text) }
    }
}

@Composable
private fun ActionIcon(iconRes: Int, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(iconRes),
            contentDescription = desc,
            tint = TextSub,
            modifier = Modifier.size(19.dp),
        )
    }
}

/** 助手「正在输入」的三点动画。 */
@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 150),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                modifier = Modifier
                    .padding(end = 5.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(TextSub.copy(alpha = alpha)),
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun AskNovieScreenPreview() {
    AskNovieScreen()
}
