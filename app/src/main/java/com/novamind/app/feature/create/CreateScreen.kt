package com.novamind.app.feature.create

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
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.feature.create.components.BgPage
import com.novamind.app.feature.create.components.ColorTextHint
import com.novamind.app.feature.create.components.ColorTextTitle
import com.novamind.app.feature.create.components.BorderColorSheet
import com.novamind.app.feature.create.components.CreateMetaRow
import com.novamind.app.feature.create.components.CreateTopBar
import com.novamind.app.ui.components.AttachmentSheet
import com.novamind.app.ui.components.DeleteConfirmSheet
import com.novamind.app.ui.components.VoiceRecordingBar
import com.novamind.app.feature.create.components.FormattingToolbar
import com.novamind.app.ui.components.ImagePreviewScreen
import com.novamind.app.feature.create.components.NoteContentEditor
import com.novamind.app.feature.create.editor.ImageBlock
import com.novamind.app.feature.create.folder.FolderPickerSheet
import com.novamind.app.feature.create.tag.TagPickerSheet
import com.novamind.app.feature.create.editor.ImageStore
import com.novamind.app.feature.create.editor.NoteDocument
import com.novamind.app.feature.create.editor.NoteEditorState
import com.novamind.app.feature.create.editor.RichSpan
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ─── Route ────────────────────────────────────────────────────────────────────

@Composable
fun CreateRoute(
    onBack: () -> Unit = {},
    noteId: String? = null,           // 非 null 时加载已有笔记
    onFullscreenChange: (Boolean) -> Unit = {},  // 全屏页（图片预览）显隐 → 宿主隐藏底部导航
    maxImages: Int = AppConfig.Media.MAX_IMAGE_PICK,  // 一次最多可选图片数
    modifier: Modifier = Modifier,
    viewModel: CreateViewModel = viewModel(),
) {
    // 进入页面时：有 noteId 则加载已有笔记，否则新建
    LaunchedEffect(noteId) {
        if (noteId != null) viewModel.loadNote(noteId)
        else viewModel.reset()
    }
    // 收一次性导航事件
    LaunchedEffect(Unit) {
        viewModel.navigateBack.collect { onBack() }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CreateScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        autoFocusBody = noteId == null,   // 新建笔记自动聚焦正文并弹出键盘
        onFullscreenChange = onFullscreenChange,
        maxImages = maxImages,
        modifier = modifier,
    )
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun CreateScreen(
    uiState: CreateUiState,
    onEvent: (CreateEvent) -> Unit,
    onBack: () -> Unit = {},
    autoFocusBody: Boolean = false,         // 新建笔记进入时自动聚焦正文（弹出键盘）
    onFullscreenChange: (Boolean) -> Unit = {},  // 图片预览全屏页显隐回调
    maxImages: Int = AppConfig.Media.MAX_IMAGE_PICK,  // 一次最多可选图片数
    modifier: Modifier = Modifier,
    forceToolbarVisible: Boolean = false,   // 预览用：强制显示格式工具栏
) {
    val imeVisible =
        WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    // 编辑已有笔记：展示其更新时间；新建：展示当前时间
    val timeLabel = remember(uiState.updatedAt) {
        TimeFormat.relative(uiState.updatedAt ?: System.currentTimeMillis())
    }

    // 悬浮工具栏在屏幕上的真实顶边（窗口坐标 px）——作为遮挡线，光标须露在其上方
    var toolbarTopWindowY by remember { mutableStateOf(Float.MAX_VALUE) }

    // 删除二次确认弹窗显隐
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 插入附件选择弹窗显隐
    var showAttachSheet by remember { mutableStateOf(false) }
    // 录音条显隐（点工具栏「Voice」后从底部弹出）
    var showRecordingBar by remember { mutableStateOf(false) }
    // 图片预览：当前预览的图片下标（null = 不显示）
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    // 图片预览或录音条打开 → 通知宿主隐藏底部导航栏；都关闭后恢复，离开本页时复位
    LaunchedEffect(previewIndex != null || showRecordingBar) {
        onFullscreenChange(previewIndex != null || showRecordingBar)
    }
    DisposableEffect(Unit) { onDispose { onFullscreenChange(false) } }

    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 图文正文编辑器状态：文本与图片块；正文文档 JSON 存入 body 同步给 ViewModel
    val editor = remember { NoteEditorState() }
    // 骨架占位（点工具栏「Magic」后出现）是否生效，由编辑器状态驱动
    val polishing = editor.isPolishing
    // 最近一次「与编辑器同步过」的 body（加载到 / 由本地编辑发出）。用它做轻量字符串比较，
    // 避免在每次 body 变化时重新 build 一遍 documentJson（getter 会全量序列化）。
    var lastSyncedBody by remember { mutableStateOf<String?>(null) }
    // 进场动画期间先不灌内容（保持轻量滑入），落定后再解析填充，避免「从首页进入卡顿」。
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(260)   // 约等于进场转场时长，让滑入先跑完
        settled = true
    }
    // 外部内容变化（加载笔记 / 撤销重做）时回填，避免与本地编辑互相覆盖。
    // 解析放后台线程（loadDocumentAsync），不阻塞主线程；仅在「非本地编辑」导致的 body 变化时重载。
    LaunchedEffect(uiState.editingNoteId, uiState.body, settled) {
        if (!settled) return@LaunchedEffect
        if (uiState.body != lastSyncedBody) {
            editor.loadDocumentAsync(uiState.body, fallbackPlain = uiState.body)
            lastSyncedBody = uiState.body
        }
    }
    val emitContent = {
        val json = editor.documentJson
        lastSyncedBody = json   // 本地编辑发出的内容，标记为已同步，避免回填重载
        onEvent(CreateEvent.ContentChanged(json))
    }

    // 新建笔记：进入后自动聚焦正文，弹出键盘；编辑已有笔记则保持收起
    LaunchedEffect(Unit) {
        if (autoFocusBody) {
            kotlinx.coroutines.delay(200)
            editor.requestInitialFocus()
        }
    }

    // 插入图片后：等图后文本块组合完成，聚焦它（光标在末尾、弹键盘）
    LaunchedEffect(editor.pendingFocus) {
        if (editor.pendingFocus != null) {
            kotlinx.coroutines.delay(30)
            editor.consumePendingFocus()
        }
    }

    // 系统照片选择器（支持多选，无需运行时权限）
    // PickMultipleVisualMedia 要求 maxItems > 1，单张场景仍按多选处理，至少为 2
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxImages.coerceAtLeast(2))
    ) { uris ->
        if (uris.isNotEmpty()) {
            var inserted = false
            uris.forEach { uri ->
                ImageStore.copyToInternal(context, uri)?.let { path ->
                    editor.insertImage(path)
                    inserted = true
                }
            }
            if (inserted) emitContent()
        }
    }

    // 相机拍照：先建目标文件拿到可写 URI，拍成功后该路径即图片
    var pendingCapturePath by remember { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCapturePath
        pendingCapturePath = null
        if (success && path != null) {
            editor.insertImage(path)
            emitContent()
        }
    }

    // 系统文件选择器（PDF / Markdown）：md 读出内容作为可渲染的 Markdown 块插入，
    // 其余（PDF 等）作为文件块插入。
    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // 选中后先校验大小：超过 16MB 直接忽略并提示（SAF 系统选择器无法按大小预先过滤）
            val size = documentSize(context, uri)
            if (size > AppConfig.Media.MAX_DOCUMENT_SIZE) {
                Toast.makeText(context, "文件超过 16MB，已忽略", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            ImageStore.copyFileToInternal(context, uri)?.let { (path, name) ->
                when {
                    isMarkdownFile(name) -> scope.launch {
                        val content = withContext(Dispatchers.IO) {
                            runCatching { File(path).readText() }.getOrDefault("")
                        }
                        if (content.isNotBlank()) editor.insertMarkdown(content)
                        else editor.insertFile(path, name)   // 空内容兜底为文件块
                        emitContent()
                    }
                    isPdfFile(name) -> {
                        editor.insertPdf(path, name)         // PDF：逐页渲染展示
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

    // 录音权限：已授权直接弹录音条，否则先申请，授权后再弹
    val recordAudioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) showRecordingBar = true }

    // 点工具栏「Voice」：收键盘并清焦点（录音内容追加到正文末尾），按需申请录音权限
    val onVoiceClicked = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) showRecordingBar = true
        else recordAudioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    // 标题与正文均为空时视为空笔记 → 禁用「更多(···)」
    val noteEmpty = uiState.title.isBlank() &&
        NoteDocument.previewText(uiState.body).isBlank()

    // 录音开始时强制收起键盘并清焦点（录音期间正文/标题置为只读，不可编辑）
    LaunchedEffect(showRecordingBar) {
        if (showRecordingBar) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    // 录音条显示时：系统返回先关闭录音条（关闭即丢弃，由 VoiceRecordingBar onDispose 取消录音），
    // 不退出笔记页。
    BackHandler(enabled = showRecordingBar) { showRecordingBar = false }

    // 选区骨架显示时：系统返回先清除骨架，不退出笔记页
    BackHandler(enabled = polishing) { editor.clearPolish() }

    // 系统返回（左/右边缘滑动返回）与左上角 back 一致：收键盘 + 保存并返回。
    // 有图片预览/弹窗/录音条时交给它们各自的返回处理（预览有自己的 BackHandler，弹窗 back 自动关闭）。
    BackHandler(
        enabled = previewIndex == null && !showAttachSheet && !showDeleteConfirm &&
            !showRecordingBar && !polishing
    ) {
        keyboardController?.hide()
        onEvent(CreateEvent.SaveNote)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgPage)
                .statusBarsPadding(),
                // adjustNothing：不在内容上用 imePadding（避免重排），键盘空间由编辑器内部处理；
                // 工具栏作为悬浮层单独用 imePadding 抬到键盘之上。
        ) {
            // ── 顶部操作行 ────────────────────────────────────────────────
            CreateTopBar(
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                onBack = {
                    keyboardController?.hide()
                    onEvent(CreateEvent.SaveNote)
                },
                onShare = {},   // 暂无事件（附件入口已移到工具栏）
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
                moreEnabled = !noteEmpty,
            )

            // ── 正文（图文混排） ──────────────────────────────────────────
            // 标题 + Meta 行作为 header 移入编辑器滚动容器，与正文一起滚动；
            // 正文填满到屏幕底部；键盘在 adjustNothing 下「盖」在上面，由编辑器内部自动滚动避让。
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                NoteContentEditor(
                    state = editor,
                    onContentChanged = emitContent,
                    readOnly = showRecordingBar,   // 录音期间正文不可编辑、不弹键盘
                    coverTopWindowY = if (imeVisible) toolbarTopWindowY else Float.MAX_VALUE,
                    onImageClick = { id ->
                        keyboardController?.hide()
                        val idx = editor.blocks.filterIsInstance<ImageBlock>().indexOfFirst { it.id == id }
                        if (idx >= 0) previewIndex = idx
                    },
                    header = {
                        // ── 标题 ──────────────────────────────────────────
                        BasicTextField(
                            value = uiState.title,
                            onValueChange = { onEvent(CreateEvent.TitleChanged(it)) },
                            readOnly = showRecordingBar,   // 录音期间不可编辑
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            textStyle = TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorTextTitle,
                            ),
                            cursorBrush = SolidColor(ColorTextTitle),
                            decorationBox = { inner ->
                                if (uiState.title.isEmpty()) {
                                    Text(
                                        "New note",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = ColorTextHint
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
                                // 打开「Add to folder」前清除焦点并收起键盘，避免键盘自动弹出
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                onEvent(CreateEvent.ShowFolderPicker)
                            },
                            onShowTagPicker = {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                onEvent(CreateEvent.ShowTagPicker)
                            },
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // ── 格式工具栏：悬浮在键盘上方（imePadding 抬升），不挤占正文 ──────────
        // 录音条出现时让位（二者都在底部，互斥显示）。
        if ((imeVisible || forceToolbarVisible) && !showRecordingBar) {
            FormattingToolbar(
                onHideKeyboard = { keyboardController?.hide() },
                onVoice = onVoiceClicked,
                onBold = { editor.toggle(RichSpan.Bold) },
                isBoldActive = editor.isActive(RichSpan.Bold),
                onItalic = { editor.toggle(RichSpan.Italic) },
                isItalicActive = editor.isActive(RichSpan.Italic),
                onInsertImage = {
                    // 工具栏附件按钮 → 打开 Image/Camera/Document 选择弹窗
                    keyboardController?.hide()
                    showAttachSheet = true
                },
                onMagic = {
                    // Magic → 有选区只对选区做骨架；未选中则对全部文字做骨架。
                    // 注意：不可 clearFocus（会丢失选区），仅收起键盘即可。
                    if (editor.startPolish()) keyboardController?.hide()
                },
                onBulletList = { editor.insertListMarker(numbered = false); emitContent() },
                onNumberedList = { editor.insertListMarker(numbered = true); emitContent() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .onGloballyPositioned { toolbarTopWindowY = it.boundsInWindow().top },
            )
        }

        // ── 录音条：从底部弹出，录音中波形/暂停/停止；完成后把音频作为附件追加到正文 ──
        if (showRecordingBar) {
            VoiceRecordingBar(
                onCancel = { showRecordingBar = false },
                onConfirm = { path, durationSeconds ->
                    showRecordingBar = false
                    editor.insertFile(path, "Recording ${formatRecordingDuration(durationSeconds)}")
                    emitContent()
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
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
                selectedHex = uiState.borderColorHex,
                onSelect = { onEvent(CreateEvent.BorderColorSelected(it)) },
                onDismiss = { onEvent(CreateEvent.DismissColorPicker) },
            )
        }

        // 删除二次确认
        if (showDeleteConfirm) {
            DeleteConfirmSheet(
                onConfirm = {
                    showDeleteConfirm = false
                    onEvent(CreateEvent.DeleteNote)
                },
                onDismiss = { showDeleteConfirm = false },
            )
        }

        // 插入附件：图片 / 拍照 / 文档
        if (showAttachSheet) {
            AttachmentSheet(
                onPickImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onTakePhoto = {
                    ImageStore.createCaptureTarget(context)?.let { (path, uri) ->
                        pendingCapturePath = path
                        cameraLauncher.launch(uri)
                    }
                },
                onPickDocument = { documentPicker.launch(DOCUMENT_MIME_TYPES) },
                onDismiss = { showAttachSheet = false },
            )
        }

        // 图片预览（全屏覆盖）：左右滑动 / 缩放 / 删除，进入/退出带淡入+缩放转场
        val liveImages = editor.blocks.filterIsInstance<ImageBlock>().map { it.path }
        // 退出动画期间 previewIndex 已置空，用上一次的快照继续渲染避免闪白
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
                    liveImages.getOrNull(page)?.let { path ->
                        editor.blocks.filterIsInstance<ImageBlock>()
                            .firstOrNull { it.path == path }
                            ?.let { editor.removeBlock(it.id); emitContent() }
                    }
                },
                deleteMessage = "This will remove the image from the note.",
                onBack = { previewIndex = null },
            )
        }

    }
}

/**
 * 文档附件可选的 MIME 类型：PDF + Markdown。
 *
 * 注意：.md 文件的 MIME 在各文件提供方很不统一——有的报 `text/markdown`，有的报
 * `text/x-markdown`，更多直接当成 `text/plain`。为保证用户能选到 .md，这里把这几类都放开
 * （代价是也会显示 .txt 等纯文本文件）。OpenDocument 只能按 MIME 过滤，无法按扩展名过滤。
 */
private val DOCUMENT_MIME_TYPES = arrayOf(
    "application/pdf",
    "text/markdown",
    "text/x-markdown",
    "text/plain",
)

/**
 * 查询 SAF 文档的字节大小；查不到（部分 Provider 不返回 SIZE）时返回 -1，
 * 此时不拦截（无法判断大小的文件照常插入）。
 */
private fun documentSize(context: android.content.Context, uri: android.net.Uri): Long {
    context.contentResolver.query(
        uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null,
    )?.use { c ->
        val idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
        if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) return c.getLong(idx)
    }
    return -1L
}

/** 是否为 Markdown 文件（按文件名后缀判断）。 */
private fun isMarkdownFile(name: String): Boolean =
    name.endsWith(".md", ignoreCase = true) || name.endsWith(".markdown", ignoreCase = true)

/** 是否为 PDF 文件（按文件名后缀判断）。 */
private fun isPdfFile(name: String): Boolean =
    name.endsWith(".pdf", ignoreCase = true)

/** 把录音秒数格式化为 m:ss，用作附件块展示名。 */
private fun formatRecordingDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

// ─── Preview ──────────────────────────────────────────────────────────────────

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
