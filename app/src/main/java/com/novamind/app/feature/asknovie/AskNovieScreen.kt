package com.novamind.app.feature.asknovie

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.asknovie.components.AssistantText
import com.novamind.app.feature.asknovie.components.AttachmentChip
import com.novamind.app.feature.asknovie.components.BareIconButton
import com.novamind.app.feature.asknovie.components.Bg
import com.novamind.app.feature.asknovie.components.Card
import com.novamind.app.feature.asknovie.components.Dark
import com.novamind.app.feature.asknovie.components.MicButton
import com.novamind.app.feature.asknovie.components.MoreMenu
import com.novamind.app.feature.asknovie.components.ScrollToBottomButton
import com.novamind.app.feature.asknovie.components.SendButton
import com.novamind.app.feature.asknovie.components.StopButton
import com.novamind.app.feature.asknovie.components.SuggestionChip
import com.novamind.app.feature.asknovie.components.TextSub
import com.novamind.app.feature.asknovie.components.TextTitle
import com.novamind.app.feature.asknovie.components.TypingIndicator
import com.novamind.app.feature.asknovie.components.UserBubble
import com.novamind.app.feature.create.editor.ImageStore
import com.novamind.app.ui.components.AttachmentSheet
import com.novamind.app.ui.components.BackButton
import com.novamind.app.ui.components.DeleteConfirmSheet
import com.novamind.app.ui.components.ImagePreviewScreen
import com.novamind.app.ui.components.VoiceRecordingBar
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.PermissionUtils
import com.novamind.app.util.TimeUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

// 配色与视觉组件统一在 feature/asknovie/components 包；本文件只做屏幕编排。

/** 预设快捷建议（点击填入输入框）。 */
private val suggestions = listOf(
    "Help me brainstorm",
    "Who have I promised to follow up",
    "Summarize my notes",
)


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
    val offTopic =
        listOf("movie", "cinema", "weather", "news", "stock", "score", "lottery", "电影", "天气")
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
 * Ask Novie 聊天入口页：顶部返回/历史/更多，中部问候或对话列表，底部快捷建议 + 输入框。
 *
 * @param userName 问候语显示的名字
 * @param onBack 返回上一页
 * @param onSend 发送消息回调
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
    var isRecording by remember { mutableStateOf(false) }        // 麦克风录音状态
    var showMoreMenu by remember { mutableStateOf(false) }       // 右上角「更多」菜单
    var showHistory by remember { mutableStateOf(false) }        // 聊天历史弹窗
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isResponding by remember { mutableStateOf(false) }       // 助手正在回复
    // 当前会话 id（用于保存到会话历史）
    var sessionId by rememberSaveable {
        mutableStateOf(UUID.randomUUID().toString())
    }
    // 手动重命名的标题（为空则用第一句话）
    var customTitle by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var attachments by remember { mutableStateOf(listOf<Attachment>()) }  // 待发送附件
    var showAttachMenu by remember { mutableStateOf(false) }             // 「+」选择菜单
    // 全屏图片预览：当前图片在「图片附件」中的下标（null 表示不显示）
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    // 「回到底部」按钮显隐：仅当最后一条真实消息超出视口下方时显示，忽略底部占位 Spacer。
    val showScrollDown by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastMsgIndex = messages.lastIndex
            if (lastMsgIndex < 0 || info.totalItemsCount == 0) {
                false
            } else {
                val visible = info.visibleItemsInfo.firstOrNull { it.index == lastMsgIndex }
                if (visible != null) {
                    // 最后一条可见：其底部超过视口下边沿 → 还有内容在下方
                    (visible.offset + visible.size) > info.viewportEndOffset + 2
                } else {
                    // 不可见：在视口上方（已滚过）→ 不显示；在下方 → 显示
                    listState.firstVisibleItemIndex < lastMsgIndex
                }
            }
        }
    }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val inputFocusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var isStreaming by remember { mutableStateOf(false) }   // 逐字输出中（流式期间不写存储）
    var responseJob by remember { mutableStateOf<Job?>(null) }   // 当前回复协程（供「停止」取消）
    // 仿 ChatGPT：刚发送的用户消息滚到顶部（自增以触发滚动，即使位置相同）
    var sendTick by remember { mutableIntStateOf(0) }
    var anchorIndex by remember { mutableIntStateOf(0) }
    // 本轮保持底部留白：发送后置 true，回复不足一屏也保留占位以免文字跳动；新建/切换会话时复位。
    var keepBottomSpace by remember { mutableStateOf(false) }
    // 列表项间距（与 LazyColumn 的 Arrangement.spacedBy 一致）
    val listItemSpacingPx = with(LocalDensity.current) { 14.dp.roundToPx() }
    // 底部占位高度（px）：仅填满「本轮内容（锚点用户消息→最后一条消息）」之外的剩余视口，
    // 使最新用户消息最多停在顶部、绝不被推出屏幕外。
    val bottomSpacerPx by remember {
        derivedStateOf {
            if (!keepBottomSpace) return@derivedStateOf 0
            val info = listState.layoutInfo
            val vp = info.viewportSize.height
            val lastMsgIndex = messages.lastIndex
            if (vp <= 0 || lastMsgIndex < 0) return@derivedStateOf 0
            val anchor = info.visibleItemsInfo.firstOrNull { it.index == anchorIndex }
            val lastReal = info.visibleItemsInfo
                .filter { it.index in 0..lastMsgIndex }
                .maxByOrNull { it.index }
            if (anchor != null && lastReal != null) {
                // 本轮内容高度 = 置顶的用户消息 + 已输出回复（与滚动位置、占位本身无关）
                val contentH = (lastReal.offset + lastReal.size) - anchor.offset
                // 剩余空白 = 视口 − 本轮内容 − 占位于上一项之间的间距（回复越长，空白越少，直至为 0）
                (vp - contentH - listItemSpacingPx).coerceAtLeast(0)
            } else {
                vp   // 测不到时退化为整屏（极少）
            }
        }
    }
    // 预览/Inspection 环境：跳过依赖 Activity 的能力（选择器、BackHandler）
    val inPreview = LocalInspectionMode.current

    // 图片选择器（系统照片选择器，多选，无需权限）；预览时不创建
    val imagePicker = if (inPreview) null else rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
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
    val filePicker = if (inPreview) null else rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            ImageStore.copyFileToInternal(context, uri)?.let { (path, name) ->
                attachments = attachments + Attachment(AttachType.File, path, name)
            }
        }
    }
    // 相机拍照：先建目标文件拿到可写 URI，拍成功后该路径即图片；预览时不创建
    var pendingCapturePath by remember { mutableStateOf<String?>(null) }
    val cameraLauncher = if (inPreview) null else rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCapturePath
        pendingCapturePath = null
        if (success && path != null) {
            attachments = attachments + Attachment(AttachType.Image, path, "photo.jpg")
        }
    }
    // 通知权限（Android 13+）：录音常驻通知需要它才能在通知栏 / 锁屏显示
    val notifPermission = if (inPreview) null else rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 录音不依赖结果，授权与否都继续；仅影响通知是否可见 */ }

    // 确保通知权限（不阻塞录音）
    val ensureNotifPermission = {
        if (PermissionUtils.needsNotificationPermission(context)) {
            notifPermission?.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 录音权限申请；授权后进入录音
    val recordPermission = if (inPreview) null else rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            keyboardController?.hide()
            ensureNotifPermission()
            isRecording = true
        } else {
            Toast.makeText(context, "需要麦克风权限才能录音", Toast.LENGTH_SHORT).show()
        }
    }

    // 追加用户消息（含附件）→ mock 回复（逐字输出 + 打字振动）；生成期间不接受新发送，可「停止」取消。
    val sendMessage: (String, List<Attachment>) -> Unit = { prompt, atts ->
        if (!isResponding && !isStreaming && (prompt.isNotEmpty() || atts.isNotEmpty())) {
            messages = messages + ChatMessage(Role.User, prompt, atts)
            anchorIndex = messages.lastIndex   // 刚发送的用户消息位置
            sendTick++                          // 触发「滚动到顶部」
            keepBottomSpace = true              // 本轮保留底部留白

            isResponding = true
            onSend(prompt)
            responseJob = scope.launch {
                try {
                    delay(450)            // 思考中（显示三点）
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
                            if (replyIndex < list.size) list[replyIndex] =
                                list[replyIndex].copy(text = text)
                        }
                        // 每隔几个字符来一次轻触感
                        if (i % 3 == 0) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        delay(24)
                    }
                } finally {
                    // 正常结束或被「停止」取消都在此复位（已输出的部分文本保留）
                    isResponding = false
                    isStreaming = false
                }
            }
        }
    }

    // 停止当前回复生成（保留已输出的部分内容）
    val stopResponse: () -> Unit = {
        responseJob?.cancel()
        responseJob = null
        isResponding = false
        isStreaming = false
    }

    // 输入框发送：取当前文本 + 附件，发送后清空
    val send: () -> Unit = {
        val prompt = input.trim()
        val atts = attachments
        // 回复生成中不发送（避免键盘 Send 键在此期间清空输入）
        if (!isResponding && !isStreaming && (prompt.isNotEmpty() || atts.isNotEmpty())) {
            input = ""
            attachments = emptyList()
            sendMessage(prompt, atts)
            // 发送后收起键盘并清焦点
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    // 发送后：把刚发送的用户消息平滑滚到顶部（仿 ChatGPT「新一页」，底部占位腾出空间供回复生成）。
    LaunchedEffect(sendTick) {
        if (sendTick > 0) listState.animateScrollToItem(anchorIndex)
    }

    // 保存当前会话到本地（含实时 / 部分回复）。切断或切换会话前调用，避免丢失正在生成的内容。
    val persistCurrentSession: () -> Unit = {
        if (messages.isNotEmpty()) {
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

    // 会话持久化：消息或标题变化即存储（流式期间不写，结束后保存一次）
    LaunchedEffect(messages, customTitle, isStreaming) {
        if (!isStreaming) persistCurrentSession()
    }

    // 录音时系统返回先退出录音
    if (!inPreview) {
        BackHandler(enabled = isRecording) { isRecording = false }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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
                BackButton(onClick = onBack, background = Card, tint = TextTitle, contentDescription = "返回")
                Spacer(Modifier.weight(1f))
                // 历史 + 更多 合并胶囊
                Surface(color = Card, shape = RoundedCornerShape(50), shadowElevation = 1.dp) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BareIconButton(
                            R.drawable.ic_history,
                            "历史",
                            onClick = { showHistory = true })
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
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(messages) { msg ->
                            // animateItem：新消息淡入 + 位置平滑过渡，发送时不突兀
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()) {
                                if (msg.role == Role.User) UserBubble(msg)
                                else AssistantText(msg.text)
                            }
                        }
                        if (isResponding) {
                            item { Box(modifier = Modifier.animateItem()) { TypingIndicator() } }
                        }
                        // 底部占位：只填满本轮内容之外的剩余视口（仿 ChatGPT），整轮保留，
                        // 回复不足一屏时底部留白且文字位置不跳动。
                        if (bottomSpacerPx > 0) {
                            item {
                                val spacerH = with(LocalDensity.current) {
                                    bottomSpacerPx.toDp()
                                }
                                Spacer(Modifier.height(spacerH))
                            }
                        }
                    }
                }

                // 内容未到底部时：悬浮「滚到最新」按钮（忽略底部占位，内容未填满屏幕时不显示）
                // 全限定：避免与外层 Column/Box 的 ColumnScope.AnimatedVisibility 扩展产生隐式 receiver 歧义
                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrollDown,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                ) {
                    ScrollToBottomButton(onClick = {
                        scope.launch { listState.smoothScrollToBottom() }
                    })
                }
            }

            // ── 底部：录音条 / 快捷建议 + 输入框 ──
            if (isRecording) {
                VoiceRecordingBar(
                    onCancel = { isRecording = false },
                    onConfirm = { path, dur ->
                        isRecording = false
                        // 录音作为语音消息发送
                        sendMessage(
                            "",
                            listOf(
                                Attachment(
                                    AttachType.Audio,
                                    path,
                                    "Voice ${TimeUtils.formatDuration(dur)}"
                                )
                            )
                        )
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
                            SuggestionChip(text = s, onClick = {
                                // 快捷发送同样收起键盘并清焦点
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                sendMessage(s, emptyList())
                            })
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 输入框（已选附件预览置于输入框内部顶部）
                    Surface(
                        color = Card,
                        shape = RoundedCornerShape(28.dp),
                        shadowElevation = 1.dp
                    ) {
                        // animateContentSize：附件增删导致的高度变化平滑过渡，避免突变
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(),
                        ) {
                            // 已选附件预览（横向滚动），位于输入框内部上方
                            if (attachments.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(start = 14.dp, end = 14.dp, top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    attachments.forEach { att ->
                                        AttachmentChip(
                                            att = att,
                                            onRemove = { attachments = attachments - att },
                                            onClick = {
                                                val idx = attachments
                                                    .filter { it.type == AttachType.Image }
                                                    .indexOfFirst { it.path == att.path }
                                                if (idx >= 0) {
                                                    // 打开全屏预览前收起键盘并清焦点
                                                    keyboardController?.hide()
                                                    focusManager.clearFocus()
                                                    previewIndex = idx
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BareIconButton(
                                    R.drawable.ic_add,
                                    "添加",
                                    onClick = {
                                        // 打开底部弹窗前先收起键盘，与笔记编辑页一致
                                        keyboardController?.hide()
                                        showAttachMenu = true
                                    },
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (input.isEmpty()) {
                                        Text(
                                            "Message with Novie",
                                            color = TextSub,
                                            fontSize = 15.sp
                                        )
                                    }
                                    BasicTextField(
                                        value = input,
                                        onValueChange = { input = it },
                                        textStyle = TextStyle(color = TextTitle, fontSize = 15.sp),
                                        cursorBrush = SolidColor(Dark),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                        keyboardActions = KeyboardActions(onSend = { send() }),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(inputFocusRequester),
                                    )
                                }

                                // 右侧按钮：回复生成中 → 停止；有内容 → 绿色发送；无内容 → 语音
                                if (isResponding || isStreaming) {
                                    StopButton(onClick = stopResponse)
                                } else if (input.isNotBlank() || attachments.isNotEmpty()) {
                                    SendButton(onClick = { send() })
                                } else {
                                    MicButton(
                                        onClick = {
                                            // 点麦克风：已授权直接录音，否则先申请权限
                                            if (PermissionUtils.hasAudioPermission(context)) {
                                                keyboardController?.hide()
                                                ensureNotifPermission()
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
        }

        // 图片附件全屏预览（覆盖整页）：左右滑动 / 缩放 / 下拉关闭 / 删除，进出带淡入缩放转场
        val imagePaths = attachments.filter { it.type == AttachType.Image }.map { it.path }
        // 退出动画期间 previewIndex 已置空，用上一次的快照继续渲染避免闪白
        var lastPreviewPaths by remember { mutableStateOf<List<String>>(emptyList()) }
        var lastPreviewIndex by remember { mutableIntStateOf(0) }
        if (previewIndex != null) {
            lastPreviewPaths = imagePaths
            lastPreviewIndex = previewIndex!!
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = previewIndex != null,
            enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.92f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.92f),
        ) {
            val shownPaths = if (previewIndex != null) imagePaths else lastPreviewPaths
            ImagePreviewScreen(
                paths = shownPaths,
                initialIndex = lastPreviewIndex,
                onDelete = { page ->
                    shownPaths.getOrNull(page)?.let { path ->
                        attachments =
                            attachments.filterNot { it.type == AttachType.Image && it.path == path }
                    }
                },
                deleteTitle = "Remove image?",
                deleteMessage = "This will remove the image from your message.",
                deleteConfirmLabel = "Remove",
                onBack = { previewIndex = null },
            )
        }
    }

    // 「+」附件选择底部弹窗：Image / Camera / Document（与笔记编辑页一致）
    if (showAttachMenu) {
        AttachmentSheet(
            onPickImage = {
                imagePicker?.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            },
            onTakePhoto = {
                ImageStore.createCaptureTarget(context)?.let { (path, uri) ->
                    pendingCapturePath = path
                    cameraLauncher?.launch(uri)
                }
            },
            onPickDocument = { filePicker?.launch(arrayOf("application/pdf")) },
            onDismiss = { showAttachMenu = false },
        )
    }

    // 聊天历史底部弹窗
    if (showHistory) {
        ChatHistorySheet(
            onDismiss = { showHistory = false },
            onNewChat = {
                showHistory = false
                // 先保存当前会话（含实时 / 部分回复），再取消生成并清空
                persistCurrentSession()
                responseJob?.cancel(); responseJob = null
                isResponding = false
                isStreaming = false
                messages = emptyList()
                input = ""
                attachments = emptyList()
                customTitle = null
                keepBottomSpace = false
                sessionId = UUID.randomUUID().toString()
            },
            onSelectSession = { s ->
                showHistory = false
                // 先保存当前会话（含实时 / 部分回复），再取消生成并切换
                persistCurrentSession()
                responseJob?.cancel(); responseJob = null
                isResponding = false
                isStreaming = false
                messages = s.messages
                sessionId = s.id
                customTitle = s.title
                input = ""
                attachments = emptyList()
                keepBottomSpace = false
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
                // 取消正在生成的回复，避免其内容串入新会话
                responseJob?.cancel(); responseJob = null
                isResponding = false
                isStreaming = false
                // 删除后重置为新会话
                messages = emptyList()
                input = ""
                attachments = emptyList()
                customTitle = null
                keepBottomSpace = false
                sessionId = UUID.randomUUID().toString()
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

/**
 * 平滑滚到底部：逐屏匀速滚动直到滚不动。
 * 不用 animateScrollToItem：其对远距离目标会先「瞬间跳转」再动画收尾（观感像直接跳底）。
 */
private suspend fun LazyListState.smoothScrollToBottom() {
    while (canScrollForward) {
        val step = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
            .toFloat()
            .coerceAtLeast(1f)
        val consumed = animateScrollBy(
            step,
            animationSpec = tween(durationMillis = 240, easing = LinearEasing)
        )
        if (consumed == 0f) break   // 已到底 / 滚不动：退出，防止死循环
    }
}

@Preview(showBackground = true, heightDp = 720, name = "AskNovie · 空状态问候")
@Composable
private fun AskNovieScreenPreview() {
    AppTheme {
        AskNovieScreen()
    }
}

@Preview(showBackground = true, heightDp = 720, name = "AskNovie · 空状态 · 深色")
@Composable
private fun AskNovieScreenDarkPreview() {
    AppTheme(darkTheme = true, dynamicColor = false) {
        AskNovieScreen()
    }
}
