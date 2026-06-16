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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import coil.compose.AsyncImage
import com.novamind.app.R
import com.novamind.app.feature.create.editor.ImageStore
import com.novamind.app.ui.components.DeleteConfirmSheet
import com.novamind.app.ui.components.VoiceRecordingBar
import kotlinx.coroutines.launch
import java.io.File

private val Bg = Color(0xFFF1EEE6)
private val Card = Color(0xFFFFFFFF)
private val TextTitle = Color(0xFF1A1A1A)
private val TextSub = Color(0xFF6B6B6B)
private val Dark = Color(0xFF1A1A1A)
private val ChipText = Color(0xFF3A3A3A)
private val SendGreen = Color(0xFF2E9E5B)
private val MenuBg = Color(0xFFF4F2EA)
private val AttachChipBg = Color(0xFFE9E7DF)

/** 预设的快捷建议（点击填入输入框）。 */
private val suggestions = listOf(
    "Help me brainstorm",
    "Who have I promised to follow up",
    "Summarize my notes",
)

/** 录音是否已授权。 */
private fun hasAudioPermission(context: android.content.Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.RECORD_AUDIO,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

/** 秒 → m:ss。 */
private fun formatDuration(sec: Int): String = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"


/** 查询 content uri 的展示文件名。 */
private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? =
    runCatching {
        context.contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

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
    // 当前会话 id（用于保存到会话历史）
    var sessionId by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(java.util.UUID.randomUUID().toString())
    }
    // 手动重命名的标题（为空则用第一句话）
    var customTitle by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 待发送附件（图片/文件）+ 「+」选择菜单显隐
    var attachments by remember { mutableStateOf(listOf<Attachment>()) }
    var showAttachMenu by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val inputFocusRequester = remember { FocusRequester() }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val context = LocalContext.current
    // 助手回复逐字输出中（避免流式期间频繁写存储）
    var isStreaming by remember { mutableStateOf(false) }
    // 预览/Inspection 环境：跳过依赖 Activity 的能力（TTS、选择器、BackHandler）
    val inPreview = androidx.compose.ui.platform.LocalInspectionMode.current

    // 语音播报（Android TTS），随页面生命周期创建与释放；预览时不创建
    val tts = if (inPreview) null else remember {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) engine.language = java.util.Locale.getDefault()
        }
        engine
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { tts?.stop(); tts?.shutdown() }
    }
    val speak: (String) -> Unit = { text ->
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "novie-tts")
    }

    // 图片选择器（系统照片选择器，支持多选，无需权限）；预览时不创建
    val imagePicker = if (inPreview) null else androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        uris.forEach { uri ->
            ImageStore.copyToInternal(context, uri)?.let { path ->
                attachments = attachments + Attachment(
                    AttachType.Image, path, queryDisplayName(context, uri) ?: "image.jpg",
                )
            }
        }
    }
    // 文件选择器；预览时不创建
    val filePicker = if (inPreview) null else androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            ImageStore.copyFileToInternal(context, uri)?.let { (path, name) ->
                attachments = attachments + Attachment(AttachType.File, path, name)
            }
        }
    }
    // 录音权限申请；授权后进入录音
    val recordPermission = if (inPreview) null else androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            keyboardController?.hide()
            isRecording = true
        } else {
            Toast.makeText(context, "需要麦克风权限才能录音", Toast.LENGTH_SHORT).show()
        }
    }

    // 追加用户消息（含附件）→ mock 回复（逐字输出 + 打字振动）
    val sendMessage: (String, List<Attachment>) -> Unit = { prompt, atts ->
        if (!isResponding && (prompt.isNotEmpty() || atts.isNotEmpty())) {
            messages = messages + ChatMessage(Role.User, prompt, atts)
            isResponding = true
            onSend(prompt)
            scope.launch {
                kotlinx.coroutines.delay(450)            // 思考中（显示三点）
                val basis = prompt.ifBlank { atts.firstOrNull()?.name ?: "" }
                val full = mockReply(basis)
                // 开始逐字输出
                isResponding = false
                isStreaming = true
                val replyIndex = messages.size
                messages = messages + ChatMessage(Role.Assistant, "")
                val sb = StringBuilder()
                full.forEachIndexed { i, ch ->
                    sb.append(ch)
                    val text = sb.toString()
                    messages = messages.toMutableList().also { list ->
                        if (replyIndex < list.size) list[replyIndex] = list[replyIndex].copy(text = text)
                    }
                    // 每隔几个字符来一次轻触感
                    if (i % 3 == 0) {
                        haptic.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                        )
                    }
                    kotlinx.coroutines.delay(24)
                }
                isStreaming = false
            }
        }
    }

    // 输入框发送：取当前文本 + 附件，发送后清空
    val send: () -> Unit = {
        val prompt = input.trim()
        val atts = attachments
        if (prompt.isNotEmpty() || atts.isNotEmpty()) {
            input = ""
            attachments = emptyList()
            sendMessage(prompt, atts)
            // 发送后保持输入框聚焦，键盘不收起
            inputFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // 新消息 / 流式增长时滚到底部
    androidx.compose.runtime.LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length, isResponding) {
        val count = messages.size + if (isResponding) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    // 会话持久化：消息或标题变化即存储（流式输出期间不写，结束后保存一次）
    androidx.compose.runtime.LaunchedEffect(messages, customTitle, isStreaming) {
        if (messages.isNotEmpty() && !isStreaming) {
            val first = messages.first()
            val title = customTitle?.takeIf { it.isNotBlank() }
                ?: first.text.trim().takeIf { it.isNotEmpty() }
                ?: first.attachments.firstOrNull()?.name
                ?: "New chat"
            ChatSessionStore.upsert(
                context,
                ChatSession(sessionId, title, System.currentTimeMillis(), messages),
            )
        }
    }

    // 录音时系统返回先退出录音
    if (!inPreview) {
        androidx.activity.compose.BackHandler(enabled = isRecording) { isRecording = false }
    }

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
                        BareIconButton(
                            R.drawable.ic_more,
                            "更多",
                            enabled = messages.isNotEmpty(),
                            onClick = { showMoreMenu = true },
                        )
                        MoreMenu(
                            expanded = showMoreMenu,
                            onDismiss = { showMoreMenu = false },
                            onShare = { showMoreMenu = false; onShare() },
                            onRename = { showMoreMenu = false; onRename(); showRename = true },
                            onExportToNotes = { showMoreMenu = false; onExportToNotes() },
                            onDelete = { showMoreMenu = false; showDeleteConfirm = true },
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
                        if (msg.role == Role.User) UserBubble(msg)
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
                onConfirm = { path, dur ->
                    isRecording = false
                    // 录音作为语音消息发送
                    sendMessage("", listOf(Attachment(AttachType.Audio, path, "Voice ${formatDuration(dur)}")))
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
                    SuggestionChip(text = s, onClick = { sendMessage(s, emptyList()) })
                }
            }

            Spacer(Modifier.height(12.dp))

            // 已选附件 chips（横向滚动）
            if (attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    attachments.forEach { att ->
                        AttachmentChip(att = att, onRemove = { attachments = attachments - att })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // 输入框
            Surface(color = Card, shape = RoundedCornerShape(28.dp), shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        BareIconButton(R.drawable.ic_add, "添加", onClick = { showAttachMenu = true })
                        AttachMenu(
                            expanded = showAttachMenu,
                            onDismiss = { showAttachMenu = false },
                            onPickImage = {
                                showAttachMenu = false
                                imagePicker?.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts
                                            .PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                            onPickFile = {
                                showAttachMenu = false
                                filePicker?.launch(arrayOf("*/*"))
                            },
                        )
                    }

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
                            keyboardActions = KeyboardActions(onSend = { send() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(inputFocusRequester),
                        )
                    }

                    // 右侧按钮：有内容 → 绿色发送；无内容 → 语音
                    if (input.isNotBlank() || attachments.isNotEmpty()) {
                        SendButton(onClick = { send() })
                    } else {
                        MicButton(
                            onClick = {
                                // 点麦克风：已授权直接录音，否则先申请权限
                                if (hasAudioPermission(context)) {
                                    keyboardController?.hide()
                                    isRecording = true
                                } else {
                                    recordPermission?.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
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
                attachments = emptyList()
                customTitle = null
                sessionId = java.util.UUID.randomUUID().toString()
            },
            onSelectSession = { s ->
                showHistory = false
                messages = s.messages
                sessionId = s.id
                customTitle = s.title
                input = ""
                attachments = emptyList()
            },
        )
    }

    // 重命名会话标题
    if (showRename) {
        val currentTitle = customTitle
            ?: messages.firstOrNull()?.let {
                it.text.trim().ifBlank { it.attachments.firstOrNull()?.name ?: "" }
            }
            ?: ""
        RenameSheet(
            initialTitle = currentTitle,
            onDismiss = { showRename = false },
            onSave = { newTitle ->
                customTitle = newTitle.ifBlank { null }
                showRename = false
            },
        )
    }

    // 删除会话二次确认
    if (showDeleteConfirm) {
        DeleteConfirmSheet(
            title = "Delete conversation?",
            message = "This will permanently delete this conversation.",
            onConfirm = {
                ChatSessionStore.delete(context, sessionId)
                // 删除后重置为新会话
                messages = emptyList()
                input = ""
                attachments = emptyList()
                customTitle = null
                sessionId = java.util.UUID.randomUUID().toString()
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
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
private fun BareIconButton(
    iconRes: Int,
    desc: String,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(iconRes),
            contentDescription = desc,
            tint = if (enabled) TextTitle else TextTitle.copy(alpha = 0.3f),
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

/** 「+」附件选择菜单：图片 / 文件。 */
@Composable
private fun AttachMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = MenuBg,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 12.dp,
        tonalElevation = 0.dp,
        // 不抢焦点，避免打开菜单时键盘被收起
        properties = androidx.compose.ui.window.PopupProperties(focusable = false),
    ) {
        DropdownMenuItem(
            text = { Text("图片", color = TextTitle, fontSize = 15.sp) },
            leadingIcon = {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_image),
                    contentDescription = null, tint = TextTitle, modifier = Modifier.size(20.dp),
                )
            },
            onClick = onPickImage,
        )
        DropdownMenuItem(
            text = { Text("文件", color = TextTitle, fontSize = 15.sp) },
            leadingIcon = {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_document),
                    contentDescription = null, tint = TextTitle, modifier = Modifier.size(20.dp),
                )
            },
            onClick = onPickFile,
        )
    }
}

/** 已选附件 chip：缩略图/图标 + 文件名 + 移除。 */
@Composable
private fun AttachmentChip(att: Attachment, onRemove: () -> Unit) {
    Surface(color = AttachChipBg, shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD8D5CC)),
                contentAlignment = Alignment.Center,
            ) {
                if (att.type == AttachType.Image) {
                    AsyncImage(
                        model = File(att.path),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(28.dp).clip(CircleShape),
                    )
                } else {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_document),
                        contentDescription = null,
                        tint = TextSub,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = att.name,
                color = TextTitle,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false),
                        onClick = onRemove,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_close),
                    contentDescription = "移除",
                    tint = TextSub,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
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

/** 语音气泡：播放/暂停 + 名称（含时长）。点击播放录音文件。 */
@Composable
private fun AudioBubble(att: Attachment) {
    val player = remember { android.media.MediaPlayer() }
    var playing by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { runCatching { player.release() } }
    }
    player.setOnCompletionListener { playing = false }

    Surface(
        color = Card,
        shape = RoundedCornerShape(50),
        shadowElevation = 1.dp,
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = {
                        runCatching {
                            if (playing) {
                                player.pause(); playing = false
                            } else {
                                if (!prepared) {
                                    player.setDataSource(att.path); player.prepare(); prepared = true
                                }
                                player.start(); playing = true
                            }
                        }
                    },
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(SendGreen),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(
                        if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                    ),
                    contentDescription = if (playing) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(att.name, color = TextTitle, fontSize = 14.sp)
        }
    }
}

/** 用户消息气泡：右对齐。附件（图片预览 / 文件 chip）在上，文本在下。 */
@Composable
private fun UserBubble(msg: ChatMessage) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(
            modifier = Modifier.padding(start = 48.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // 语音附件：可播放气泡
            msg.attachments.filter { it.type == AttachType.Audio }.forEach { att ->
                AudioBubble(att)
            }
            // 图片附件：圆角预览
            msg.attachments.filter { it.type == AttachType.Image }.forEach { att ->
                AsyncImage(
                    model = File(att.path),
                    contentDescription = att.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .widthIn(max = 220.dp)
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFE3E0D8)),
                )
            }
            // 文件附件：静态 chip
            msg.attachments.filter { it.type == AttachType.File }.forEach { att ->
                Surface(
                    color = AttachChipBg,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_document),
                            contentDescription = null,
                            tint = TextSub,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            att.name,
                            color = TextTitle,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp),
                        )
                    }
                }
            }
            // 文本气泡（有文字才显示）
            if (msg.text.isNotEmpty()) {
                Surface(
                    color = Card,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 1.dp,
                ) {
                    SelectionContainer {
                        Text(
                            text = msg.text,
                            color = TextTitle,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
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
