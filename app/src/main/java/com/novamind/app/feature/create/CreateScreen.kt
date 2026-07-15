package com.novamind.app.feature.create

import com.novamind.app.ui.colors.current
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.BackgroundColors

import android.widget.Toast
import com.novamind.app.common.config.AppConfig
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.novamind.app.R
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.components.LoadingIndicator
import com.novamind.app.ui.components.LoadingOverlay
import com.novamind.app.feature.create.components.BorderColorSheet
import com.novamind.app.feature.create.components.ShareAccessScreen
import com.novamind.app.feature.create.components.CreateMetaRow
import com.novamind.app.feature.create.components.CreateTopBar
import com.novamind.app.ui.components.AttachmentSheet
import com.novamind.app.ui.components.DeleteConfirmSheet
import com.novamind.app.ui.components.VoiceRecordingBar
import com.novamind.app.feature.create.components.FormattingToolbar
import com.novamind.app.ui.components.ImagePreviewScreen
import com.novamind.app.feature.create.components.NoteContentEditor
import com.novamind.app.feature.create.editor.ImageBlock
import com.novamind.app.feature.create.editor.UploadState
import com.novamind.app.feature.create.folder.FolderPickerSheet
import com.novamind.app.feature.create.tag.TagPickerSheet
import com.novamind.app.feature.create.editor.ImageStore
import com.novamind.app.feature.create.editor.NoteDocument
import com.novamind.app.feature.create.editor.NoteEditorState
import com.novamind.app.feature.create.editor.RichSpan
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.FileUtils
import com.novamind.app.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun CreateScreen(
    uiState: CreateUiState,
    onEvent: (CreateEvent) -> Unit,
    onBack: () -> Unit = {},
    autoFocusBody: Boolean = false,         // 新建笔记进入时自动聚焦正文（弹出键盘）
    isEditing: Boolean = false,             // 编辑进入（打开已有笔记）：即使无内容也可用「更多」；新建页保持原样
    onFullscreenChange: (Boolean) -> Unit = {},  // 图片预览全屏页显隐回调
    maxImages: Int = AppConfig.Media.MAX_IMAGE_PICK,  // 一次最多可选图片数
    readOnly: Boolean = false,              // 回收站只读态：仅查看，不可编辑
    onRestore: () -> Unit = {},             // 只读态「Restore」
    onDeleteForever: () -> Unit = {},       // 只读态「Delete」（彻底删除）
    // 图片上传：给本地路径 + contentType，返回服务端 fileId（由 CreateRoute 接 ViewModel）
    onUploadImage: suspend (String, String) -> Result<String> = { _, _ ->
        Result.failure(IllegalStateException("upload not wired"))
    },
    // 附件 fileId → 签名下载 URL（打开已有笔记后由 ViewModel 提供，供本地图失效时兜底渲染）
    attachmentUrls: Map<String, String> = emptyMap(),
    // 录音发送后上传为笔记源录音（§7）：给本地路径 + 时长(ms)，由 CreateRoute 接 ViewModel
    onUploadRecording: (String, Long) -> Unit = { _, _ -> },
    // 取消进行中的源录音上传（进度条上的 ×）
    onCancelUploadRecording: () -> Unit = {},
    // 源录音上传成功的一次性事件：到达后关闭录音面板
    recordingUploaded: kotlinx.coroutines.flow.Flow<Unit>? = null,
    // 转写结果就绪的一次性事件：携带 HTML + ack，追加进正文后 complete(ack) 通知 VM（§9）
    transcriptionReady: kotlinx.coroutines.flow.Flow<TranscriptionInsert>? = null,
    modifier: Modifier = Modifier,
    forceToolbarVisible: Boolean = false,   // 预览用：强制显示格式工具栏
) {
    val imeVisible =
        WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    // 编辑已有笔记：展示其更新时间；新建：展示当前时间。保存中则临时显示「Saving…」。
    val savedTimeLabel = remember(uiState.updatedAt) {
        TimeUtils.smart(uiState.updatedAt ?: System.currentTimeMillis())
    }
    val timeLabel = when {
        uiState.isTranscribing -> "Transcribing…"
        uiState.isSaving -> "Saving…"
        else -> savedTimeLabel
    }

    // 工具栏顶边（窗口 px），作为光标遮挡线
    var toolbarTopWindowY by remember { mutableStateOf(Float.MAX_VALUE) }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showRecordingBar by remember { mutableStateOf(false) }
    // 上传成功事件到达 → 关闭录音面板（面板在确认后保持显示，直到这里收到成功）
    LaunchedEffect(recordingUploaded) {
        recordingUploaded?.collect { showRecordingBar = false }
    }
    // 图片预览的下标（null = 不显示）
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var showShare by remember { mutableStateOf(false) }
    // 全屏层（预览/录音/分享）打开时通知宿主隐藏底部导航
    LaunchedEffect(previewIndex != null || showRecordingBar || showShare) {
        onFullscreenChange(previewIndex != null || showRecordingBar || showShare)
    }
    DisposableEffect(Unit) { onDispose { onFullscreenChange(false) } }

    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 图文正文编辑器状态（文本 + 图片块），文档 JSON 同步给 ViewModel
    val editor = remember { NoteEditorState() }
    val polishing = editor.isPolishing
    // 字数上限：标题 + 正文合计
    val maxInputChars = AppConfig.Editor.MAX_INPUT_CHARS
    val titleLen = uiState.title.length
    val bodyLen = editor.textLength
    val totalChars = titleLen + bodyLen
    // 最近同步过的 body，用字符串比较避免每次变化都重建 documentJson
    var lastSyncedBody by remember { mutableStateOf<String?>(null) }
    // 进场动画期间不灌内容，落定后再解析填充，避免入场卡顿
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(260)
        settled = true
    }
    // 正文是否已完成首次加载（按笔记重置）。转写填充需等它为真，否则 append 会被随后的
    // loadDocumentAsync 回填覆盖（进页即 READY 的场景尤其容易踩到）。
    var bodyLoaded by remember(uiState.editingNoteId) { mutableStateOf(false) }
    // 外部内容变化（加载/撤销重做）时回填，后台解析，避免与本地编辑互相覆盖
    LaunchedEffect(uiState.editingNoteId, uiState.body, settled) {
        if (!settled) return@LaunchedEffect
        if (uiState.body != lastSyncedBody) {
            editor.loadDocumentAsync(uiState.body, fallbackPlain = uiState.body)
            lastSyncedBody = uiState.body
        }
        bodyLoaded = true
    }
    val emitContent = {
        val json = editor.documentJson
        lastSyncedBody = json   // 标记为已同步，避免回填重载
        onEvent(CreateEvent.ContentChanged(json))
    }

    // 转写结果就绪（§9）→ 追加进正文并同步保存。此时正文只读，但程序化写入不受影响。
    // 先等正文首次加载完成，避免进页即 READY 时 append 被随后的回填覆盖。
    LaunchedEffect(transcriptionReady) {
        transcriptionReady?.collect { insert ->
            snapshotFlow { bodyLoaded }.first { it }
            editor.appendHtml(insert.html)   // 转写文本为 HTML（Speaker 加粗+配色、分段换行）
            emitContent()                    // 同步更新 uiState.body
            insert.ack.complete(Unit)        // 明确通知 VM：UI 已追加完成，可安全保存
        }
    }

    // 上传已插入的图片并回填 fileId / 上传态；完成后 emitContent 让正文带上 fileId（供保存时对账挂附件）
    val startUpload: (ImageBlock) -> Unit = { block ->
        editor.updateImage(block.id) { it.copy(uploadState = UploadState.UPLOADING) }
        scope.launch {
            val ext = block.path.substringAfterLast('.', "").lowercase()
            val contentType = if (ext == "png") "image/png" else "image/jpeg"
            val result = onUploadImage(block.path, contentType)
            editor.updateImage(block.id) {
                it.copy(
                    fileId = result.getOrNull() ?: it.fileId,
                    uploadState = if (result.isSuccess) UploadState.UPLOADED else UploadState.FAILED,
                )
            }
            emitContent()
        }
    }

    // 打开已有笔记后拿到 fileId→签名 URL 时，回填到对应图片块，供本地路径失效时渲染
    LaunchedEffect(attachmentUrls) {
        if (attachmentUrls.isEmpty()) return@LaunchedEffect
        editor.blocks.filterIsInstance<ImageBlock>().forEach { b ->
            val url = b.fileId?.let { attachmentUrls[it] }
            if (url != null && b.remoteUrl != url) editor.updateImage(b.id) { it.copy(remoteUrl = url) }
        }
    }

    // 新建笔记：进入后自动聚焦正文弹键盘；编辑已有笔记保持收起
    LaunchedEffect(Unit) {
        if (autoFocusBody) {
            kotlinx.coroutines.delay(200)
            editor.requestInitialFocus()
        }
    }

    // 插入图片后：聚焦其后的文本块（光标末尾、弹键盘）
    LaunchedEffect(editor.pendingFocus) {
        if (editor.pendingFocus != null) {
            kotlinx.coroutines.delay(30)
            editor.consumePendingFocus()
        }
    }

    // 附件上限：图片/PDF/Markdown 合计；可选图片数 = 上限 − 已有附件
    val remainingSlots = (AppConfig.Media.MAX_ATTACHMENTS - editor.attachmentCount).coerceAtLeast(0)
    val imagePickMax = minOf(maxImages, remainingSlots)

    // 仅剩 1 个名额用单选选择器（多选页 API 要求 maxItems>1）
    val singleImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) scope.launch {
            // 导入（下采样+压缩+读宽高）放 IO 线程
            val saved = withContext(Dispatchers.IO) { ImageStore.importImage(context, uri) }
            saved?.let {
                val block = editor.insertImage(it.path, it.width, it.height)
                emitContent()
                startUpload(block)
            }
        }
    }

    // 多选选择器：maxItems = 剩余名额（key 变化时重建），返回后按名额截断
    val multiImagePicker = key(imagePickMax) {
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(imagePickMax.coerceAtLeast(2))
        ) { uris ->
            if (uris.isNotEmpty()) scope.launch {
                val saved = uris.take(imagePickMax).mapNotNull { uri ->
                    withContext(Dispatchers.IO) { ImageStore.importImage(context, uri) }
                }
                saved.forEach { s -> startUpload(editor.insertImage(s.path, s.width, s.height)) }
                if (saved.isNotEmpty()) emitContent()
            }
        }
    }

    // 相机拍照：先建目标文件拿可写 URI，成功后下采样压缩并读宽高
    var pendingCapturePath by remember { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCapturePath
        pendingCapturePath = null
        if (success && path != null) scope.launch {
            val saved = withContext(Dispatchers.IO) { ImageStore.finalizeCaptured(context, path) }
            val block = editor.insertImage(saved.path, saved.width, saved.height)
            emitContent()
            startUpload(block)
        }
    }

    // 文件选择器：md 作为 Markdown 块插入，其余（PDF 等）作为文件块
    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // 先校验大小：超过 16MB 忽略并提示（SAF 无法预先按大小过滤）
            val size = FileUtils.documentSize(context, uri)
            if (size > AppConfig.Media.MAX_DOCUMENT_SIZE) {
                Toast.makeText(context, "File exceeds 16MB, skipped", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            ImageStore.copyFileToInternal(context, uri)?.let { (path, name) ->
                when {
                    FileUtils.isMarkdownFile(name) -> scope.launch {
                        val content = withContext(Dispatchers.IO) {
                            runCatching { File(path).readText() }.getOrDefault("")
                        }
                        if (content.isNotBlank()) editor.insertMarkdown(content)
                        else editor.insertFile(path, name)   // 空内容兜底为文件块
                        emitContent()
                    }

                    FileUtils.isPdfFile(name) -> {
                        editor.insertPdf(path, name)
                        emitContent()
                    }

                    else -> {
                        editor.insertFile(path, name)
                        emitContent()
                    }
                }
            }
        }
    }

    // 录音权限：已授权直接弹录音条，否则先申请
    val recordAudioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) showRecordingBar = true }

    // 点「Voice」：收键盘清焦点，按需申请录音权限
    val onVoiceClicked = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) showRecordingBar = true
        else recordAudioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    // 标题与正文均为空 → 禁用「更多(···)」
    val noteEmpty = uiState.title.isBlank() &&
            NoteDocument.previewText(uiState.body).isBlank()

    // 录音开始时收键盘清焦点（录音期间标题/正文只读）
    LaunchedEffect(showRecordingBar) {
        if (showRecordingBar) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    // 返回优先级：录音条 → 骨架 → 保存返回；预览/弹窗各自处理返回
    BackHandler(enabled = showRecordingBar) { showRecordingBar = false }
    BackHandler(enabled = polishing) { editor.clearPolish() }
    BackHandler(
        enabled = previewIndex == null && !showAttachSheet && !showDeleteConfirm &&
                !showRecordingBar && !polishing && !showShare
    ) {
        keyboardController?.hide()
        // 只读态直接返回不落盘；编辑态返回即保存
        if (readOnly) onBack() else onEvent(CreateEvent.SaveNote)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundColors.Page.default.current())
                .statusBarsPadding(),
            // adjustNothing：内容不用 imePadding（避免重排），工具栏作为悬浮层单独抬升
        ) {
            // ── 顶部操作行 ────────────────────────────────────────────────
            CreateTopBar(
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                onBack = {
                    keyboardController?.hide()
                    // 只读态直接返回（不落盘）；编辑态返回即保存
                    if (readOnly) onBack() else onEvent(CreateEvent.SaveNote)
                },
                onShare = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    showShare = true
                },
                onUndo = { onEvent(CreateEvent.UndoEdit) },
                onRedo = { onEvent(CreateEvent.RedoEdit) },
                onChangeColor = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onEvent(CreateEvent.ShowColorPicker)
                },
                onDelete = {
                    keyboardController?.hide()
                    showDeleteConfirm = true
                },
                // 编辑进入（打开已有笔记）：即使无内容也可用「更多」→ 删除 / 改颜色；
                // 新建页保持原样：仅笔记非空时可用。
                moreEnabled = isEditing || !noteEmpty,
                readOnly = readOnly,
                onRestore = onRestore,
                onDeleteForever = { showDeleteConfirm = true },   // 二次确认后彻底删除
            )

            // ── 正文（图文混排） ──────────────────────────────────────────
            // 标题 + Meta 行作为 header 随正文一起滚动；键盘由编辑器内部滚动避让
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                NoteContentEditor(
                    state = editor,
                    onContentChanged = emitContent,
                    readOnly = showRecordingBar || readOnly || uiState.isTranscribing,   // 录音 / 回收站只读 / 转写中：正文不可编辑、不弹键盘
                    bodyCharLimit = (maxInputChars - titleLen).coerceAtLeast(0),
                    coverTopWindowY = if (imeVisible) toolbarTopWindowY else Float.MAX_VALUE,
                    onImageClick = { id ->
                        keyboardController?.hide()
                        val idx = editor.blocks.filterIsInstance<ImageBlock>()
                            .indexOfFirst { it.id == id }
                        if (idx >= 0) previewIndex = idx
                    },
                    onImageRetry = { startUpload(it) },
                    header = {
                        // ── 标题 ──────────────────────────────────────────
                        BasicTextField(
                            value = uiState.title,
                            onValueChange = {
                                // 标题 + 正文合计不超上限；超限的「增长型」修改拒绝
                                if (it.length + bodyLen <= maxInputChars || it.length <= uiState.title.length) {
                                    onEvent(CreateEvent.TitleChanged(it))
                                }
                            },
                            readOnly = showRecordingBar || readOnly || uiState.isTranscribing,   // 录音 / 回收站只读 / 转写中不可编辑
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            textStyle = TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextColors.Primary.default.current(),
                            ),
                            cursorBrush = SolidColor(TextColors.Primary.default.current()),
                            decorationBox = { inner ->
                                if (uiState.title.isEmpty()) {
                                    Text(
                                        "New note",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = TextColors.Primary.tertiary.current()
                                    )
                                }
                                inner()
                            },
                        )

                        // ── Meta 操作行 ───────────────────────────────────
                        CreateMetaRow(
                            selectedFolder = uiState.selectedFolder,
                            selectedTags = uiState.selectedTags,
                            timeLabel = timeLabel,
                            onShowFolderPicker = {
                                // 打开前清焦点收键盘，避免键盘自动弹出
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                onEvent(CreateEvent.ShowFolderPicker)
                            },
                            onShowTagPicker = {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                onEvent(CreateEvent.ShowTagPicker)
                            },
                            readOnly = readOnly,
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // ── 格式工具栏：悬浮在键盘上方，与录音条互斥 ──────────
        if ((imeVisible || forceToolbarVisible) && !showRecordingBar && !readOnly) {
            FormattingToolbar(
                onHideKeyboard = { keyboardController?.hide() },
                onVoice = onVoiceClicked,
                onBold = { editor.toggle(RichSpan.Bold) },
                isBoldActive = editor.isActive(RichSpan.Bold),
                onItalic = { editor.toggle(RichSpan.Italic) },
                isItalicActive = editor.isActive(RichSpan.Italic),
                onInsertImage = {
                    // 打开附件选择弹窗；已满则提示
                    if (remainingSlots <= 0) {
                        Toast.makeText(
                            context,
                            "You can add up to ${AppConfig.Media.MAX_ATTACHMENTS} attachments",
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        keyboardController?.hide()
                        showAttachSheet = true
                    }
                },
                onMagic = {
                    // 有选区只对选区做骨架，否则全文；不清焦点以保留选区
                    editor.startPolish()
                },
                onBulletList = { editor.insertListMarker(numbered = false); emitContent() },
                onNumberedList = { editor.insertListMarker(numbered = true); emitContent() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .onGloballyPositioned { toolbarTopWindowY = it.boundsInWindow().top },
            )
        }

        // ── 字数计数：右下角「当前/上限」，达上限标红；只读态不展示 ──
        // 低于展示阈值显示实际字数；达到/超过阈值则统一显示为上限（避免临近上限时数字频繁跳动）。
        if (!showRecordingBar && !readOnly) {
            val toolbarShown = (imeVisible || forceToolbarVisible)
            val displayCount =
                if (totalChars < AppConfig.Editor.COUNT_DISPLAY_THRESHOLD) "$totalChars" else {
                    "Remaining $maxInputChars-$totalChars "
                }
            Text(
                text = displayCount,
                fontSize = 11.sp,
                color = if (totalChars >= maxInputChars) IconColors.Error.default.current() else TextColors.Primary.tertiary.current(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = if (toolbarShown) 84.dp else 12.dp),
            )
        }

        // ── 底部堆叠：上传进度条（§7）在上，录音板在下 ──
        // 录音板自带 navigationBarsPadding；仅当录音板不在时才给整列补底部系统栏留白。
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .then(if (!showRecordingBar) Modifier.navigationBarsPadding() else Modifier),
        ) {
            // 源录音上传进度条：录音发送后直传云端时展示，可取消
            AnimatedVisibility(
                visible = uiState.isUploadingAudio,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
            ) {
                AudioUploadBar(
                    progress = uiState.audioUploadProgress,
                    onCancel = onCancelUploadRecording,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                )
            }
            // 录音板：确认后不插入正文，仅触发上传（§7）；面板保持显示直到收到 recordingUploaded（上传成功）
            if (showRecordingBar) {
                VoiceRecordingBar(
                    onCancel = { showRecordingBar = false },
                    onConfirm = { path, durationSeconds ->
                        onUploadRecording(path, durationSeconds * 1000L)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ── BottomSheet ───────────────────────────────────────────────────
        if (uiState.showTagPicker) {
            TagPickerSheet(
                availableTags = uiState.availableTags,
                selectedTags = uiState.selectedTags,
                onTagToggle = { onEvent(CreateEvent.TagToggled(it)) },
                onNewTag = { onEvent(CreateEvent.NewTagCreated(it)) },
                onDismiss = { onEvent(CreateEvent.DismissTagPicker) },
            )
        }

        if (uiState.showFolderPicker) {
            FolderPickerSheet(
                folders = uiState.availableFolders,
                selectedFolder = uiState.selectedFolder,
                onFolderSelect = { onEvent(CreateEvent.FolderSelected(it)) },
                onNewFolder = { onEvent(CreateEvent.NewFolderCreated(it)) },
                onDismiss = { onEvent(CreateEvent.DismissFolderPicker) },
            )
        }

        if (uiState.showColorPicker) {
            BorderColorSheet(
                selectedColor = uiState.borderColor,
                onSelect = { onEvent(CreateEvent.BorderColorSelected(it)) },
                onDismiss = { onEvent(CreateEvent.DismissColorPicker) },
            )
        }

        // 删除二次确认
        if (showDeleteConfirm) {
            DeleteConfirmSheet(
                onConfirm = {
                    showDeleteConfirm = false
                    // 只读态（回收站）：彻底删除交宿主 RecycleBinViewModel 走服务端 DELETE + 重拉（单一来源，避免重复删）；
                    // 编辑态：软删除，移入回收站（PATCH {trashed:true}）
                    if (readOnly) onDeleteForever() else onEvent(CreateEvent.DeleteNote)
                },
                onDismiss = { showDeleteConfirm = false },
            )
        }

        // 插入附件：图片 / 拍照 / 文档
        if (showAttachSheet) {
            AttachmentSheet(
                onPickImage = {
                    val req =
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    // 只剩 1 个名额走单选，否则走多选（maxItems = 剩余名额）
                    if (imagePickMax <= 1) singleImagePicker.launch(req)
                    else multiImagePicker.launch(req)
                },
                onTakePhoto = {
                    ImageStore.createCaptureTarget(context)?.let { (path, uri) ->
                        pendingCapturePath = path
                        cameraLauncher.launch(uri)
                    }
                },
                onPickDocument = { documentPicker.launch(AppConfig.Media.DOCUMENT_MIME_TYPES) },
                onDismiss = { showAttachSheet = false },
            )
        }

        // 图片预览（全屏覆盖）：滑动/缩放/删除，带淡入+缩放转场
        // 每张解析为可渲染模型：本地文件存在用本地路径，否则用签名网络 URL（他机加载的笔记）
        val liveImages = editor.blocks.filterIsInstance<ImageBlock>().map { b ->
            b.path.takeIf { File(it).exists() } ?: b.remoteUrl ?: b.path
        }
        // 退出动画期间 previewIndex 已置空，用上次快照续渲染避免闪白
        var lastPreviewPaths by remember { mutableStateOf<List<String>>(emptyList()) }
        var lastPreviewIndex by remember { mutableStateOf(0) }
        if (previewIndex != null) {
            lastPreviewPaths = liveImages
            lastPreviewIndex = previewIndex!!
        }
        AnimatedVisibility(
            visible = previewIndex != null,
            enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.92f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.92f),
        ) {
            ImagePreviewScreen(
                paths = if (previewIndex != null) liveImages else lastPreviewPaths,
                initialIndex = lastPreviewIndex,
                onDelete = { page ->
                    // 按序号删除对应图片块（liveImages 可能含网络 URL，不能按 path 匹配）
                    editor.blocks.filterIsInstance<ImageBlock>().getOrNull(page)?.let {
                        editor.removeBlock(it.id)
                        emitContent()
                    }
                },
                deleteMessage = "This will remove the image from the note.",
                onBack = { previewIndex = null },
            )
        }

        // 分享访问（全屏覆盖）：从右侧推入
        AnimatedVisibility(
            visible = showShare,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            ShareAccessScreen(
                onBack = { showShare = false },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 语音转文字加载遮罩：转写轮询期间显示（拦截交互），语音全部转写完成后消失。
        LoadingOverlay(
            visible = uiState.isTranscribing,
            message = "Transcribing your audio… This usually takes 1–10 minutes. Please check back shortly.",
        )
    }
}

// ─── 源录音上传进度条（§7）────────────────────────────────────────────────────

/**
 * 录音发送后直传云端时的进度条：左侧转圈，中间「Uploading for transcription…」+ 副标题，
 * 右侧百分比与关闭按钮，底部一条确定进度的进度条。[progress] 取 0..1；[onCancel] 取消上传。
 */
@Composable
private fun AudioUploadBar(
    progress: Float,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pct = (progress.coerceIn(0f, 1f) * 100).toInt()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = BackgroundColors.Surface.default.current(),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColors.Default.default.current()),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(size = 22.dp, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Uploading for transcription…",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextColors.Primary.default.current(),
                    )
                    Text(
                        text = "This may take a moment.",
                        fontSize = 11.sp,
                        color = TextColors.Primary.tertiary.current(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$pct%",
                    fontSize = 12.sp,
                    color = TextColors.Primary.tertiary.current(),
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Cancel upload",
                    tint = TextColors.Primary.tertiary.current(),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onCancel)
                        .padding(4.dp)
                        .size(18.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = IconColors.Brand.default.current(),
                trackColor = BackgroundColors.Primary.tertiary.current(),
            )
        }
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Audio upload bar")
@Composable
private fun AudioUploadBarPreview() {
    AppTheme {
        AudioUploadBar(progress = 0.5f, onCancel = {}, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CreateScreenPreview() {
    AppTheme {
        CreateScreen(
            uiState = CreateUiState(),
            onEvent = {},
            forceToolbarVisible = true,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Read-only (Trash)")
@Composable
private fun CreateScreenReadOnlyPreview() {
    AppTheme {
        CreateScreen(
            uiState = CreateUiState(
                editingNoteId = "1",
                title = "Q3 marketing campaign",
                body = "Meeting Summary\n• Q3 Strategy: Reviewed competitor analysis and finalized the budget for the upcoming product launch.\n• Team Offsite: Scheduled at Mount Serenity.",
                updatedAt = System.currentTimeMillis(),
            ),
            onEvent = {},
            readOnly = true,
        )
    }
}
