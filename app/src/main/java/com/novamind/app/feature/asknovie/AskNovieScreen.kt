package com.novamind.app.feature.asknovie

import com.novamind.app.util.ToastUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.asknovie.components.AssistantText
import com.novamind.app.feature.asknovie.components.AttachmentChip
import com.novamind.app.feature.asknovie.components.BareIconButton
import com.novamind.app.feature.asknovie.components.Bg
import com.novamind.app.feature.asknovie.components.Card
import com.novamind.app.feature.asknovie.components.ComposerRoundButton
import com.novamind.app.feature.asknovie.components.CreateNoteCta
import com.novamind.app.feature.asknovie.components.Dark
import com.novamind.app.feature.asknovie.components.Hint
import com.novamind.app.feature.asknovie.components.ModelPill
import com.novamind.app.feature.asknovie.components.MoreMenu
import com.novamind.app.feature.asknovie.components.NoteResultCard
import com.novamind.app.feature.asknovie.components.QuadrantDiagram
import com.novamind.app.feature.asknovie.components.ScrollToBottomButton
import com.novamind.app.feature.asknovie.components.SendButton
import com.novamind.app.feature.asknovie.components.SkillStatusRow
import com.novamind.app.feature.asknovie.components.StopButton
import com.novamind.app.feature.asknovie.components.SuggestionChip
import com.novamind.app.feature.asknovie.components.SseCard
import com.novamind.app.feature.asknovie.components.TextSub
import com.novamind.app.feature.asknovie.components.TextTitle
import com.novamind.app.feature.asknovie.components.TypingIndicator
import com.novamind.app.feature.asknovie.components.UserBubble
import com.novamind.app.feature.asknovie.data.ChatCard
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.feature.create.editor.ImageStore
import com.novamind.app.ui.components.AttachmentSheet
import com.novamind.app.ui.components.AppAlertDialog
import com.novamind.app.ui.components.ImagePreviewScreen
import com.novamind.app.ui.components.RecordingUploadOutcome
import com.novamind.app.ui.components.VoiceRecordingBar
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.PermissionUtils
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

// 配色与视觉组件统一在 feature/asknovie/components 包；本文件只做屏幕编排。

private const val ASK_NOVIE_MAX_VOICE_SECONDS = 60
private const val ASK_NOVIE_MAX_IMAGES = 5
private const val VOICE_TRANSCRIPTION_STEP_DELAY_MS = 60L

/** 预设快捷建议（点击填入输入框）。 */
private val suggestions = listOf(
    "Help me brainstorm",
    "Who have I promised to follow up with?",
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
        listOf("movie", "cinema", "weather", "news", "stock", "score", "lottery")
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
 * 是否触发 agentic 工具流演示（澄清 → visualise 技能 → 生成笔记）。
 * 命中这些短语即进入脚本化演示（对应 Figma「look back on this year」场景）。
 */
private fun isAgenticTrigger(prompt: String): Boolean {
    val p = prompt.lowercase()
    return listOf("look back", "lookback", "cs hire", "first cs", "visualise", "visualize", "cs look like")
        .any { p.contains(it) }
}

/** agentic 演示用的内联象限图数据（对应 Figma「First CS hire」）。 */
private val DEMO_QUADRANT = ChatBlock.Quadrant(
    title = "First CS hire — seniority × specialization",
    subtitle = "Sarah's framing, visualized",
    topAxis = "Onboarding-focused",
    bottomAxis = "Generalist",
    leftAxis = "Junior",
    rightAxis = "Senior",
    cells = listOf(
        QuadrantCell(
            "Junior + focused",
            listOf("Cheap, narrow coaching cost", "Scope is the role", "— training window is bounded"),
            highlight = true,
            badge = "◆ SARAH'S TARGET",
        ),
        QuadrantCell(
            "Senior + focused",
            listOf("Expensive, low coaching cost", "Senior for a narrow scope", "— may feel small to them"),
        ),
        QuadrantCell(
            "Junior + broad",
            listOf("Cheap but heavy coaching", "Broad CS scope + junior", "≈ 10 hrs/week back on you"),
        ),
        QuadrantCell(
            "Senior + broad",
            listOf("Expensive AND scope creep risk", "Senior generalists", "reshape the role"),
        ),
    ),
)

/**
 * Ask Novie 聊天入口页：顶部返回/历史/更多，中部问候或对话列表，底部快捷建议 + 输入框。
 *
 * @param userName 问候语显示的名字（用户昵称，来自全局 UserSession）；为空/空白时问候语退化为「Hi there」。
 * @param newSessionRequestId 大于 0 且尚未消费时，保存当前会话并打开空白新会话。
 * @param onBack 返回上一页
 * @param onSend 发送消息回调
 * @param initial* 仅供 @Preview 注入初始状态；生产调用用默认值（空），不影响行为
 */
@Composable
fun AskNovieScreen(
    userName: String? = null,
    newSessionRequestId: Long = 0L,
    onBack: () -> Unit = {},
    onSend: (String) -> Unit = {},
    onShare: () -> Unit = {},
    onRename: () -> Unit = {},
    onExportToNotes: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
    initialMessages: List<ChatMessage> = emptyList(),
    initialInput: String = "",
    initialAttachments: List<Attachment> = emptyList(),
    initialResponding: Boolean = false,
) {
    // Preview 没有稳定的 Activity/Application ViewModel 环境，使用纯 Compose 本地状态。
    // 真机运行时仍由 Activity 级 ViewModel 持有会话和 SSE 任务。
    val inPreview = LocalInspectionMode.current
    var input by remember { mutableStateOf(initialInput) }
    var isRecording by remember { mutableStateOf(false) }        // 麦克风录音状态
    var transcribedVoiceText by remember { mutableStateOf("") }
    var showMoreMenu by remember { mutableStateOf(false) }       // 右上角「更多」菜单
    var showHistory by remember { mutableStateOf(false) }        // 聊天历史弹窗
    // 会话状态存放在 Activity 作用域的 VM，切走页面（AnimatedVisibility 移出 composition）
    // 再回来不丢；流式回复亦跑在其 viewModelScope 上，切走不取消、SSE 数据继续累积。
    val chatVm: AskNovieChatViewModel? = if (inPreview) null else viewModel()
    val previewMessages = remember { mutableStateOf(initialMessages) }
    val previewStreamingText = remember { mutableStateOf("") }
    val previewResponding = remember { mutableStateOf(initialResponding) }
    val previewStreaming = remember { mutableStateOf(false) }
    val previewResponseJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val previewSessionId = remember { mutableStateOf("preview-session") }
    val previewCustomTitle = remember { mutableStateOf<String?>(null) }
    // 首次进入用初始/预览参数播种；之后保留既有会话（回到页面不重置）。
    remember {
        if (chatVm != null && !chatVm.seeded) {
            chatVm.messages.value = initialMessages
            chatVm.isResponding.value = initialResponding
            chatVm.markSeeded()
        }
        true
    }
    var messages by (chatVm?.messages ?: previewMessages)
    val streamingTextState = chatVm?.streamingText ?: previewStreamingText
    var isResponding by (chatVm?.isResponding ?: previewResponding) // 助手正在回复
    var sessionId by (chatVm?.sessionId ?: previewSessionId)         // 当前会话 id
    var customTitle by (chatVm?.customTitle ?: previewCustomTitle)   // 手动重命名的标题
    // options Card 以底部弹层展示；记住已答/已关闭的消息位置，避免重组后反复弹出。
    var handledOptionCardIndexes by remember(sessionId) { mutableStateOf(emptySet<Int>()) }
    val latestOptionCard = messages.withIndex().lastOrNull { (_, message) ->
        message.card is ChatCard.Options
    }
    val activeOptionCard = latestOptionCard?.takeIf { (index, _) ->
        index !in handledOptionCardIndexes &&
            messages.drop(index + 1).none { it.role == Role.User }
    }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 底部模型胶囊：当前模型 + 选择弹窗
    var selectedModel by rememberSaveable { mutableStateOf("Opus 4.8") }
    var showModelPicker by remember { mutableStateOf(false) }
    // agentic 工具流弹窗：澄清问题 / 生成笔记
    var showClarify by remember { mutableStateOf(false) }
    var showCreateNote by remember { mutableStateOf(false) }
    var createNoteTitle by remember { mutableStateOf("First CS hire") }
    var attachments by remember { mutableStateOf(initialAttachments) }   // 待发送附件
    var uploadingAttachmentPaths by remember { mutableStateOf(emptySet<String>()) }
    var showAttachMenu by remember { mutableStateOf(false) }             // 「+」选择菜单
    // 全屏图片预览：当前图片在「图片附件」中的下标（null 表示不显示）
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    // adjustNothing + imePadding 会让 LazyColumn 的视口在键盘收起时变大；如果列表正处于底部，
    // Compose 会默认把内容跟着新的底边向下带。记录键盘可见期间的首项位置，收起后恢复该位置，
    // 让内容保持原来的视觉位置，不因键盘动画自动下拉。
    var imeWasVisible by remember { mutableStateOf(false) }
    var imeAnchorIndex by remember { mutableIntStateOf(listState.firstVisibleItemIndex) }
    var imeAnchorOffset by remember { mutableIntStateOf(listState.firstVisibleItemScrollOffset) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            imeWasVisible = true
            snapshotFlow {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }.collect { (index, offset) ->
                imeAnchorIndex = index
                imeAnchorOffset = offset
            }
        } else if (imeWasVisible) {
            withFrameNanos { }
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems > 0) {
                listState.scrollToItem(
                    imeAnchorIndex.coerceIn(0, totalItems - 1),
                    imeAnchorOffset,
                )
            }
            imeWasVisible = false
        }
    }

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
    val context = LocalContext.current
    var isStreaming by (chatVm?.isStreaming ?: previewStreaming) // 逐字输出中
    var responseJob by (chatVm?.responseJob ?: previewResponseJob) // 当前回复协程
    // 仿 ChatGPT：刚发送的用户消息滚到顶部（自增以触发滚动，即使位置相同）
    var sendTick by remember { mutableIntStateOf(0) }
    var anchorIndex by remember { mutableIntStateOf(0) }
    // 本轮保持底部留白：发送后置 true，回复不足一屏也保留占位以免文字跳动；新建/切换会话时复位。
    var keepBottomSpace by remember { mutableStateOf(false) }
    var bottomSpacerPx by remember { mutableIntStateOf(0) }
    // 列表项间距（与 LazyColumn 的 Arrangement.spacedBy 一致）
    val listItemSpacingPx = with(LocalDensity.current) { 14.dp.roundToPx() }
    val imageCount = attachments.count { it.type == AttachType.Image }
    val remainingImageSlots = (ASK_NOVIE_MAX_IMAGES - imageCount).coerceAtLeast(0)
    val imageLimitMessage = "You can attach up to $ASK_NOVIE_MAX_IMAGES images."
    val addAndUploadImage: (Attachment) -> Unit = { attachment ->
        if (attachments.count { it.type == AttachType.Image } >= ASK_NOVIE_MAX_IMAGES) {
            runCatching { java.io.File(attachment.path).delete() }
            ToastUtils.short(context, imageLimitMessage)
        } else {
            attachments = attachments + attachment
            uploadingAttachmentPaths = uploadingAttachmentPaths + attachment.path
            scope.launch {
                val result = chatVm?.uploadAttachment(attachment)
                    ?: Result.success("preview-file-id")
                result.fold(
                    onSuccess = { fileId ->
                        attachments = attachments.map { current ->
                            if (current.path == attachment.path) {
                                current.copy(remoteFileId = fileId)
                            } else {
                                current
                            }
                        }
                        uploadingAttachmentPaths = uploadingAttachmentPaths - attachment.path
                    },
                    onFailure = { error ->
                        attachments = attachments.filterNot { it.path == attachment.path }
                        uploadingAttachmentPaths = uploadingAttachmentPaths - attachment.path
                        ToastUtils.short(
                            context,
                            error.message ?: "Could not upload the image. Please retry.",
                        )
                    },
                )
            }
        }
    }
    // 图片选择器把输入框剩余名额传给系统；只剩 1 个名额时切换为单选契约。
    val singleImagePicker = if (inPreview) null else rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            ImageStore.copyToInternal(context, it)?.let { path ->
                addAndUploadImage(
                    Attachment(
                        AttachType.Image,
                        path,
                        queryDisplayName(context, it) ?: "image.jpg",
                    ),
                )
            }
        }
    }
    val multiImagePicker = if (inPreview) null else key(remainingImageSlots) {
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(remainingImageSlots.coerceAtLeast(2))
        ) { uris ->
            uris.take(remainingImageSlots).forEach { uri ->
                ImageStore.copyToInternal(context, uri)?.let { path ->
                    addAndUploadImage(
                        Attachment(
                            AttachType.Image,
                            path,
                            queryDisplayName(context, uri) ?: "image.jpg",
                        ),
                    )
                }
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
            addAndUploadImage(Attachment(AttachType.Image, path, "photo.jpg"))
        }
    }
    // 启动系统相机（先建目标文件拿到可写 URI）
    val launchCamera: () -> Unit = {
        if (attachments.count { it.type == AttachType.Image } >= ASK_NOVIE_MAX_IMAGES) {
            ToastUtils.short(context, imageLimitMessage)
        } else {
            ImageStore.createCaptureTarget(context)?.let { (path, uri) ->
                pendingCapturePath = path
                cameraLauncher?.launch(uri)
            }
        }
    }
    // 相机权限：清单声明了 CAMERA，运行时必须持有该权限才能启动拍照，否则系统抛 SecurityException
    val cameraPermission = if (inPreview) null else rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            ToastUtils.short(context, "Camera permission is required to take a photo")
        }
    }
    // 点「拍照」：已授权直接启动相机，否则先申请相机权限
    val takePhoto: () -> Unit = {
        if (PermissionUtils.hasCameraPermission(context)) {
            launchCamera()
        } else {
            cameraPermission?.launch(android.Manifest.permission.CAMERA)
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
            ensureNotifPermission()
            isRecording = true
        } else {
            ToastUtils.short(context, "Microphone permission is required to record audio")
        }
    }

    // 追加用户消息（含附件）→ mock 回复（逐字输出 + 打字振动）；生成期间不接受新发送，可「停止」取消。
    val sendMessage: (String, List<Attachment>) -> Unit = { prompt, atts ->
        if (
            !isResponding &&
            !isStreaming &&
            uploadingAttachmentPaths.isEmpty() &&
            (prompt.isNotEmpty() || atts.isNotEmpty())
        ) {
            messages = messages + ChatMessage(Role.User, prompt, atts)
            anchorIndex = messages.lastIndex   // 刚发送的用户消息位置
            sendTick++                          // 触发「滚动到顶部」
            keepBottomSpace = true              // 本轮保留底部留白

            onSend(prompt)

            if (isAgenticTrigger(prompt)) {
                // agentic 演示：助手先追问一句，再弹出澄清问题弹窗
                isResponding = true
                responseJob = scope.launch {
                    try {
                        delay(600)
                        isResponding = false
                        messages = messages + ChatMessage(
                            Role.Assistant,
                            "A few things to sharpen the picture. What does CS look like at " +
                                "Nova today — mostly onboarding new customers, ongoing account " +
                                "work, or reactive support?",
                        )
                        delay(300)
                        showClarify = true
                    } finally {
                        isResponding = false
                    }
                }
            } else {
                // SSE 由 Activity 级 ViewModel 消费，切走页面或切换 App 窗口不会丢失服务端增量。
                chatVm?.startStreamingReply(prompt, atts)
            }
        }
    }

    // 停止当前回复生成（保留已输出的部分内容）
    val stopResponse: () -> Unit = {
        chatVm?.stopStreamingReply()
        responseJob?.cancel()
    }

    // Card 操作/常驻入口通过 action 重新进入同一会话，不伪造空的用户气泡。
    val sendAction: (String) -> Unit = { action ->
        if (!isResponding && !isStreaming) {
            keyboardController?.hide()
            focusManager.clearFocus()
            keepBottomSpace = true
            chatVm?.startStreamingReply(prompt = "", action = action)
        }
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
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    // ── agentic 工具流（脚本化演示）：澄清 → visualise 技能 → 生成笔记 ──
    // 澄清作答：记录答案 → 运行 visualise 技能 → 象限图 → 「Create as a note」按钮
    val onClarifyAnswered: (String) -> Unit = { answer ->
        showClarify = false
        messages = messages + ChatMessage(Role.User, answer)
        anchorIndex = messages.lastIndex
        sendTick++
        keepBottomSpace = true
        responseJob = scope.launch {
            isResponding = true
            messages = messages + ChatMessage(
                Role.Assistant, "", block = ChatBlock.SkillStatus("using visualise skill"),
            )
            delay(1200)
            messages = messages + ChatMessage(Role.Assistant, "", block = DEMO_QUADRANT)
            delay(500)
            messages = messages + ChatMessage(Role.Assistant, "", block = ChatBlock.CreateNoteCta)
            isResponding = false
        }
    }
    // 「Create as a note」：打开生成笔记弹窗
    val onCreateNoteRequested: () -> Unit = {
        keyboardController?.hide()
        createNoteTitle = "First CS hire"
        showCreateNote = true
    }
    // 生成笔记确认：运行「创建笔记」技能 → 完成态 + 笔记卡片 + 收尾语
    val onNoteCreated: (String) -> Unit = { title ->
        showCreateNote = false
        val finalTitle = title.ifBlank { "First CS hire" }
        responseJob = scope.launch {
            isResponding = true
            messages = messages + ChatMessage(
                Role.Assistant, "", block = ChatBlock.SkillStatus("Creating notes now.."),
            )
            delay(1200)
            messages = messages + ChatMessage(
                Role.Assistant, "Creation of $finalTitle note is done.", dim = true,
            )
            messages = messages + ChatMessage(
                Role.Assistant, "",
                block = ChatBlock.NoteResult(
                    title = finalTitle,
                    body = "Junior + focused hire. Cheap, narrow coaching cost; " +
                        "scope is the role; training window is bounded.",
                    dateLabel = "AUG 1   10:00AM",
                ),
            )
            delay(300)
            messages = messages + ChatMessage(
                Role.Assistant,
                "Anything else you want to sharpen, or ready to move on?",
                showAvatar = true,
            )
            isResponding = false
        }
    }

    // 发送后：把刚发送的用户消息平滑滚到顶部（仿 ChatGPT「新一页」，底部占位腾出空间供回复生成）。
    LaunchedEffect(sendTick) {
        if (sendTick > 0) {
            listState.animateScrollToItem(anchorIndex)
            // 等待用户消息完成布局后固定本轮底部留白。流式 Markdown 高度持续变化时若同步缩小
            // Spacer，会让 LazyColumn 每帧同时重测消息和占位，形成可见闪动。
            withFrameNanos { }
            val info = listState.layoutInfo
            val anchor = info.visibleItemsInfo.firstOrNull { it.index == anchorIndex }
            bottomSpacerPx = if (keepBottomSpace && anchor != null) {
                (info.viewportSize.height - anchor.size - listItemSpacingPx).coerceAtLeast(0)
            } else {
                0
            }
        }
    }

    // 切换历史会话后，等待新消息列表完成一次布局，再定位到最后一个实际 item。
    LaunchedEffect(sessionId) {
        withFrameNanos { }
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex >= 0) listState.scrollToItem(lastIndex)
    }

    // 底部留白只服务于本轮回复生成过程；回复完成后立即移除，避免空白一直保留。
    LaunchedEffect(isResponding, isStreaming) {
        if (!isResponding && !isStreaming) {
            keepBottomSpace = false
            bottomSpacerPx = 0
        }
    }

    // 保存当前会话到本地（含实时 / 部分回复）。切断或切换会话前调用，避免丢失正在生成的内容。
    val persistCurrentSession: () -> Unit = {
        if (!inPreview && messages.isNotEmpty()) {
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

    // 开始新会话：先保存当前会话（含实时 / 部分回复），再取消生成并清空全部状态。
    // 顶部「+」与历史弹窗「New chat」共用。
    val startNewChat: () -> Unit = {
        persistCurrentSession()
        if (chatVm != null) {
            chatVm.startNewSession()
        } else {
            messages = emptyList()
            isResponding = false
            isStreaming = false
            sessionId = "preview-session"
            customTitle = null
        }
        input = ""
        attachments = emptyList()
        keepBottomSpace = false
    }

    LaunchedEffect(newSessionRequestId) {
        if (chatVm?.consumeNewSessionRequest(newSessionRequestId) == true) startNewChat()
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
            // ── 顶部栏（Figma：返回 · 新会话 / 历史 / 更多，均为无底色圆形按钮）──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BareIconButton(R.drawable.ic_arrow_back, "Back", onClick = onBack)
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BareIconButton(
                        R.drawable.ic_add,
                        "New chat",
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            startNewChat()
                        },
                    )
                    BareIconButton(
                        R.drawable.ic_history,
                        "History",
                        onClick = {
                            // 打开历史前释放输入框焦点，避免弹窗切换期间光标继续闪烁。
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            // 流式回复进行中也先写入当前快照，历史列表打开即可看到并可安全切换。
                            persistCurrentSession()
                            showHistory = true
                        },
                    )
                    Box {
                        BareIconButton(
                            R.drawable.ic_more,
                            "More",
                            onClick = { showMoreMenu = true },
                        )
                        MoreMenu(
                            expanded = showMoreMenu,
                            hasConversation = messages.isNotEmpty(),
                            onDismiss = { showMoreMenu = false },
                            onShare = { showMoreMenu = false; onShare() },
                            onRename = { showMoreMenu = false; onRename(); showRename = true },
                            onExportToNotes = { showMoreMenu = false; onExportToNotes() },
                            onStartGrilling = {
                                showMoreMenu = false
                                sendAction("start_grilling")
                            },
                            onEndGrilling = {
                                showMoreMenu = false
                                sendAction("end_grilling")
                            },
                            onDelete = { showMoreMenu = false; showDeleteConfirm = true },
                        )
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
                    // 空状态（Figma）：品牌花标 + 问候 + 竖排快捷建议，靠左顶部对齐。
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                            .padding(top = 24.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ask_novie_header_symbol),
                            contentDescription = null,
                            tint = TextTitle,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = userName?.takeIf { it.isNotBlank() }?.let { "Hi, $it" } ?: "Hi there",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextTitle,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "What’s on your mind?",
                            fontSize = 14.sp,
                            color = TextSub,
                        )
                        // 录音态按 Figma 键盘收起状态保留空白内容区，不显示快捷建议，
                        // 让底部波形录音条成为唯一操作焦点。
                        if (!isRecording) {
                            Spacer(Modifier.height(28.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                suggestions.forEach { s ->
                                    SuggestionChip(text = s, onClick = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        sendMessage(s, emptyList())
                                    })
                                }
                            }
                        }
                    }
                } else {
                    // 会话切换时重建 LazyColumn，取消旧会话的 animateItem 动画，避免新旧内容重影。
                    key(sessionId) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = 16.dp,
                                vertical = 12.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            itemsIndexed(
                                items = messages,
                                key = { index, _ -> "message-$index" },
                            ) { index, msg ->
                                val isTypingAssistant =
                                    isStreaming && index == messages.lastIndex && msg.role == Role.Assistant
                                // animateItem：新消息淡入 + 位置平滑过渡，发送时不突兀
                                // 流式消息的高度持续变化，若保留 animateItem 会每次触发位置动画，
                                // 与 Markdown 重新测量叠加后造成页面抖动。仅流式期间关闭。
                                val itemModifier = Modifier.fillMaxWidth().let { base ->
                                    if (isTypingAssistant) base else base.animateItem()
                                }
                                Box(modifier = itemModifier) {
                                    if (msg.role == Role.User) {
                                        UserBubble(msg)
                                    } else if (msg.card is ChatCard.Options) {
                                        // options 卡片由底部弹层承载，不在消息流重复渲染。
                                    } else if (msg.card != null) {
                                        SseCard(
                                            card = msg.card,
                                            onSendText = { answer ->
                                                sendMessage(answer, emptyList())
                                            },
                                            onAction = sendAction,
                                        )
                                    } else when (val b = msg.block) {
                                        // agentic 富内容块
                                        is ChatBlock.SkillStatus -> SkillStatusRow(b.label, b.working)
                                        is ChatBlock.Quadrant -> QuadrantDiagram(b)
                                        is ChatBlock.NoteResult -> NoteResultCard(b, onClick = {
                                            ToastUtils.short(context, "Opening note…")
                                        })
                                        ChatBlock.CreateNoteCta -> CreateNoteCta(onClick = onCreateNoteRequested)
                                        // 纯文本助手消息（收尾语带花标、状态行为灰字）
                                        null -> when {
                                            msg.showAvatar -> AssistantText(
                                                msg.text,
                                                showAvatar = true,
                                                isTyping = isTypingAssistant,
                                            )
                                            msg.dim -> Text(
                                                msg.text,
                                                color = TextSub,
                                                fontSize = 14.sp,
                                                lineHeight = 20.sp,
                                            )
                                            else -> AssistantText(
                                                // 在消息 item 的组合域内读取，避免流式文字变化使整个 Screen 失效。
                                                text = if (isTypingAssistant) streamingTextState.value else msg.text,
                                                isTyping = isTypingAssistant,
                                            )
                                        }
                                    }
                                }
                            }
                            if (isResponding) {
                                item(key = "typing") {
                                    Box(modifier = Modifier.animateItem()) { TypingIndicator() }
                                }
                            }
                            // 底部占位：只填满本轮内容之外的剩余视口（仿 ChatGPT），整轮保留，
                            // 回复不足一屏时底部留白且文字位置不跳动。
                            if (bottomSpacerPx > 0) {
                                item(key = "bottom-spacer") {
                                    val spacerH = with(LocalDensity.current) {
                                        bottomSpacerPx.toDp()
                                    }
                                    Spacer(Modifier.height(spacerH))
                                }
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
                        .align(Alignment.BottomCenter),
                ) {
                    // Figma 862:62035：54dp 高的页面色渐隐遮罩，黑色圆形按钮距顶 6dp。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Bg),
                                ),
                            ),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(modifier = Modifier.padding(top = 6.dp)) {
                            ScrollToBottomButton(onClick = {
                                scope.launch { listState.smoothScrollToBottom() }
                            })
                        }
                    }
                }
            }

            // ── 底部：录音条 / 快捷建议 + 输入框 ──
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                        .alpha(if (isRecording) 0f else 1f),
                ) {
                    // 输入卡片（Figma：占位/文本在上，控件行在下；圆角 20）
                    Surface(
                        color = Card,
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 1.dp,
                    ) {
                        // 输入卡片不做整体尺寸动画，避免文本测量时出现先变高再回缩的抖动。
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            // 已选附件预览（横向滚动），位于卡片内部上方
                            if (attachments.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    attachments.forEach { att ->
                                        AttachmentChip(
                                            att = att,
                                            onRemove = {
                                                attachments = attachments - att
                                                uploadingAttachmentPaths =
                                                    uploadingAttachmentPaths - att.path
                                            },
                                            isUploading = att.path in uploadingAttachmentPaths,
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

                            // 首行：占位 / 输入
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 22.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (input.isEmpty()) {
                                    Text(
                                        "Message with Novie",
                                        color = Hint,
                                        fontSize = 16.sp,
                                        lineHeight = 22.sp,
                                    )
                                }
                                BasicTextField(
                                    value = input,
                                    onValueChange = { input = it },
                                    textStyle = TextStyle(
                                        color = TextTitle,
                                        fontSize = 16.sp,
                                        lineHeight = 22.sp,
                                    ),
                                    cursorBrush = SolidColor(Dark),
                                    // Figma：多行自增长（最多约 6 行后内部滚动），换行用回车，发送用按钮
                                    singleLine = false,
                                    minLines = 1,
                                    maxLines = 6,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(inputFocusRequester),
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            // 次行：左 [+][模型胶囊]，右 [语音][发送 / 停止]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ComposerRoundButton(
                                        R.drawable.ic_add,
                                        "Add",
                                        iconSize = 16.dp,
                                        onClick = {
                                            // 打开底部弹窗前先收起键盘，与笔记编辑页一致
                                            keyboardController?.hide()
                                            showAttachMenu = true
                                        },
                                    )
                                    // 模型选择暂时隐藏（后续接入真实模型切换时再放开）
                                    // ModelPill(
                                    //     selectedModel,
                                    //     onClick = {
                                    //         keyboardController?.hide()
                                    //         focusManager.clearFocus()
                                    //         showModelPicker = true
                                    //     },
                                    // )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ComposerRoundButton(
                                        R.drawable.ic_mic,
                                        "Voice",
                                        onClick = {
                                            // 点麦克风：已授权直接录音，否则先申请权限
                                            if (PermissionUtils.hasAudioPermission(context)) {
                                                ensureNotifPermission()
                                                isRecording = true
                                            } else {
                                                recordPermission?.launch(android.Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                    )
                                    // 回复生成中 → 停止；否则 → 发送（无输入内容时置灰不可点）
                                    if (isResponding || isStreaming) {
                                        StopButton(onClick = stopResponse)
                                    } else {
                                        SendButton(
                                            enabled = uploadingAttachmentPaths.isEmpty() &&
                                                (input.isNotBlank() || attachments.isNotEmpty()),
                                            onClick = { send() },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isRecording) {
                    VoiceRecordingBar(
                        onCancel = { isRecording = false },
                        onConfirm = { path, dur ->
                            isRecording = false
                            runCatching { java.io.File(path).delete() }
                            val transcript = transcribedVoiceText.trim()
                            transcribedVoiceText = ""
                            if (transcript.isBlank()) {
                                ToastUtils.short(
                                    context,
                                    "We couldn't hear any speech. Please try again.",
                                )
                            } else {
                                // 转译成功后直接发送最终文本，不再重新聚焦输入框等待二次确认。
                                send()
                            }
                        },
                        onUpload = { path, dur ->
                            if (dur > ASK_NOVIE_MAX_VOICE_SECONDS) {
                                ToastUtils.short(
                                    context,
                                    "Voice input can be up to 60 seconds.",
                                )
                                RecordingUploadOutcome.DiscardFailure
                            } else if (!java.io.File(path).isFile) {
                                ToastUtils.short(
                                    context,
                                    "The recording is unavailable. Please record again.",
                                )
                                RecordingUploadOutcome.DiscardFailure
                            } else if (chatVm == null) {
                                RecordingUploadOutcome.DiscardFailure
                            } else {
                                when (val result = chatVm.transcribeVoice(path, dur)) {
                                    is ApiResult.Success -> {
                                        val transcription = result.data
                                        if (transcription?.text.isNullOrBlank()) {
                                            ToastUtils.short(
                                                context,
                                                "We couldn't hear any speech. Please try again.",
                                            )
                                            return@VoiceRecordingBar RecordingUploadOutcome.DiscardFailure
                                        }
                                        val baseInput = input.trimEnd()
                                        val steps = transcription?.partialTexts.orEmpty()
                                            .ifEmpty { listOf(transcription?.text.orEmpty()) }
                                        steps.forEachIndexed { index, partialText ->
                                            transcribedVoiceText = partialText
                                            input = listOf(baseInput, partialText)
                                                .filter { it.isNotBlank() }
                                                .joinToString(" ")
                                            if (index < steps.lastIndex) {
                                                delay(VOICE_TRANSCRIPTION_STEP_DELAY_MS)
                                            }
                                        }
                                        // 中间步骤只用于逐步覆盖展示；最终强制以数组末项为准。
                                        transcribedVoiceText = transcription?.text.orEmpty()
                                        input = listOf(baseInput, transcribedVoiceText)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" ")
                                        RecordingUploadOutcome.Success
                                    }
                                    is ApiResult.BizError -> {
                                        ToastUtils.short(
                                            context,
                                            result.message ?: "Couldn't transcribe the recording.",
                                        )
                                        if (
                                            result.httpStatus >= 500 ||
                                            result.httpStatus == 408 ||
                                            result.httpStatus == 429
                                        ) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                    )
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
                val request = PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                )
                when {
                    remainingImageSlots <= 0 -> ToastUtils.short(context, imageLimitMessage)
                    remainingImageSlots == 1 -> singleImagePicker?.launch(request)
                    else -> multiImagePicker?.launch(request)
                }
            },
            onTakePhoto = takePhoto,
            onPickDocument = { filePicker?.launch(arrayOf("application/pdf")) },
            onDismiss = { showAttachMenu = false },
        )
    }

    // 聊天历史全屏页：保留退场期间的 composition，使返回动画能完整播放。
    androidx.compose.animation.AnimatedVisibility(
        visible = showHistory,
        enter = slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(durationMillis = 300),
        ) + fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(durationMillis = 260),
        ) + fadeOut(animationSpec = tween(durationMillis = 180)),
    ) {
        ChatHistoryScreen(
            onBack = { showHistory = false },
            onSelectSession = { s ->
                keyboardController?.hide()
                focusManager.clearFocus()
                showHistory = false
                // 仅切换展示会话：旧会话的 SSE 继续在后台接收并保存。
                persistCurrentSession()
                if (chatVm != null) {
                    chatVm.selectSession(s)
                } else {
                    messages = s.messages
                    sessionId = s.id
                    customTitle = s.title
                }
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
        AppAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = "Delete conversation?",
            message = "This will permanently delete this conversation.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            destructive = true,
            onConfirm = {
                if (chatVm != null) {
                    chatVm.deleteCurrentSession()
                } else {
                    messages = emptyList()
                    sessionId = "preview-session"
                    customTitle = null
                }
                input = ""
                attachments = emptyList()
                keepBottomSpace = false
                showDeleteConfirm = false
                onDelete()
            },
        )
    }

    // 模型选择底部弹窗（点击「模型胶囊」弹出）
    if (showModelPicker) {
        ModelPickerSheet(
            selected = selectedModel,
            onSelect = { selectedModel = it; showModelPicker = false },
            onDismiss = { showModelPicker = false },
        )
    }

    // 澄清问题弹窗（agentic 工具流）
    if (showClarify) {
        ClarifyQuestionSheet(
            question = "What does CS look like at Nova today?",
            options = listOf(
                "Mostly onboarding",
                "Ongoing account work",
                "Reactive support",
                "Mix",
            ),
            onSelect = { _, opt -> onClarifyAnswered(opt) },
            onSubmitOther = { onClarifyAnswered(it) },
            onDismiss = { showClarify = false },
        )
    }

    activeOptionCard?.let { (index, message) ->
        OptionsCardSheet(
            card = message.card as ChatCard.Options,
            onSubmit = { answer ->
                handledOptionCardIndexes = handledOptionCardIndexes + index
                sendMessage(answer, emptyList())
            },
            onDismiss = {
                handledOptionCardIndexes = handledOptionCardIndexes + index
            },
        )
    }

    // 生成笔记弹窗（agentic 工具流）
    if (showCreateNote) {
        CreateNoteSheet(
            initialTitle = createNoteTitle,
            folderName = "Team meetings",
            onCreate = { onNoteCreated(it) },
            onDismiss = { showCreateNote = false },
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

// ─── Preview ───

/** 预览用示例对话。 */
private val previewMessages = listOf(
    ChatMessage(Role.User, "Help me organize the key points of this quarterly report"),
    ChatMessage(
        Role.Assistant,
        "Sure, here are the three key points of this quarterly report: revenue grew 12% year-over-year, gross margin stabilized, and cash flow turned positive. Want me to break it into next steps?",
    ),
    ChatMessage(Role.User, "Also list a to-do"),
)

@Preview(showBackground = true, heightDp = 720, name = "AskNovie · Empty State Greeting")
@Composable
private fun AskNovieScreenPreview() {
    AppTheme {
        AskNovieScreen()
    }
}

@Preview(showBackground = true, heightDp = 720, name = "AskNovie · Empty State · Dark")
@Composable
private fun AskNovieScreenDarkPreview() {
    AppTheme(darkTheme = true, dynamicColor = false) {
        AskNovieScreen()
    }
}

@Preview(showBackground = true, heightDp = 720, name = "AskNovie · Conversation")
@Composable
private fun AskNovieScreenConversationPreview() {
    AppTheme {
        AskNovieScreen(initialMessages = previewMessages)
    }
}

@Preview(showBackground = true, heightDp = 720, name = "AskNovie · Conversation · Dark")
@Composable
private fun AskNovieScreenConversationDarkPreview() {
    AppTheme(darkTheme = true, dynamicColor = false) {
        AskNovieScreen(initialMessages = previewMessages)
    }
}

@Preview(showBackground = true, heightDp = 720, name = "AskNovie · Assistant Responding")
@Composable
private fun AskNovieScreenRespondingPreview() {
    AppTheme {
        AskNovieScreen(
            initialMessages = listOf(ChatMessage(Role.User, "Help me summarize today's meeting")),
            initialResponding = true,
        )
    }
}

@Preview(showBackground = true, heightDp = 720, name = "AskNovie · Text + Attachment Input")
@Composable
private fun AskNovieScreenComposingPreview() {
    AppTheme {
        AskNovieScreen(
            initialInput = "Extract the key points from this image",
            initialAttachments = listOf(
                Attachment(AttachType.Image, "/preview/photo.jpg", "photo.jpg"),
                Attachment(AttachType.File, "/preview/report.pdf", "report.pdf"),
            ),
        )
    }
}
