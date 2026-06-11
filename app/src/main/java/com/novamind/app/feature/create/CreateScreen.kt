package com.novamind.app.feature.create

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.novamind.app.feature.create.components.CreateMetaRow
import com.novamind.app.feature.create.components.AttachmentSheet
import com.novamind.app.feature.create.components.CreateTopBar
import com.novamind.app.feature.create.components.DeleteConfirmSheet
import com.novamind.app.feature.create.components.FormattingToolbar
import com.novamind.app.feature.create.components.ImagePreviewScreen
import com.novamind.app.feature.create.components.NoteContentEditor
import com.novamind.app.feature.create.editor.ImageBlock
import com.novamind.app.feature.create.folder.FolderPickerSheet
import com.novamind.app.feature.create.tag.TagPickerSheet
import com.novamind.app.feature.create.editor.ImageStore
import com.novamind.app.feature.create.editor.NoteEditorState
import com.novamind.app.feature.create.editor.RichSpan
import com.novamind.app.ui.theme.AppTheme
import java.util.Date

// ─── Route ────────────────────────────────────────────────────────────────────

@Composable
fun CreateRoute(
    onBack: () -> Unit = {},
    noteId: String? = null,           // 非 null 时加载已有笔记
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
    modifier: Modifier = Modifier,
    forceToolbarVisible: Boolean = false,   // 预览用：强制显示格式工具栏
) {
    val imeVisible =
        WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    val timeLabel = remember { DateFormat.format("Today HH:mm", Date()).toString() }

    // 悬浮工具栏在屏幕上的真实顶边（窗口坐标 px）——作为遮挡线，光标须露在其上方
    var toolbarTopWindowY by remember { mutableStateOf(Float.MAX_VALUE) }

    // 删除二次确认弹窗显隐
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 插入附件选择弹窗显隐
    var showAttachSheet by remember { mutableStateOf(false) }
    // 图片预览：当前预览的图片下标（null = 不显示）
    var previewIndex by remember { mutableStateOf<Int?>(null) }

    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val context = LocalContext.current

    // 图文正文编辑器状态：文本与图片块；正文文档 JSON 存入 body 同步给 ViewModel
    val editor = remember { NoteEditorState() }
    // 外部内容变化（加载笔记 / 撤销重做）时回填，避免与本地编辑互相覆盖
    LaunchedEffect(uiState.editingNoteId, uiState.body) {
        if (uiState.body != editor.documentJson) {
            editor.loadDocument(uiState.body, fallbackPlain = uiState.body)
        }
    }
    val emitContent = { onEvent(CreateEvent.ContentChanged(editor.documentJson)) }

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
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
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

    // 系统文件选择器（任意文档），插入为文件块
    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            ImageStore.copyFileToInternal(context, uri)?.let { (path, name) ->
                editor.insertFile(path, name)
                emitContent()
            }
        }
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
                onShare = {
                    keyboardController?.hide()
                    showAttachSheet = true
                },
                onUndo = { onEvent(CreateEvent.UndoEdit) },
                onRedo = { onEvent(CreateEvent.RedoEdit) },
                onDelete = {
                    keyboardController?.hide()
                    showDeleteConfirm = true
                },
            )

            // ── 标题 ──────────────────────────────────────────────────────
            BasicTextField(
                value = uiState.title,
                onValueChange = { onEvent(CreateEvent.TitleChanged(it)) },
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

            // ── Meta 操作行 ───────────────────────────────────────────────
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

            // ── 正文（图文混排） ──────────────────────────────────────────
            // 正文填满到屏幕底部；键盘在 adjustNothing 下「盖」在上面，由编辑器内部自动滚动避让。
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                NoteContentEditor(
                    state = editor,
                    onContentChanged = emitContent,
                    coverTopWindowY = if (imeVisible) toolbarTopWindowY else Float.MAX_VALUE,
                    onImageClick = { id ->
                        keyboardController?.hide()
                        val idx = editor.blocks.filterIsInstance<ImageBlock>().indexOfFirst { it.id == id }
                        if (idx >= 0) previewIndex = idx
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // ── 格式工具栏：悬浮在键盘上方（imePadding 抬升），不挤占正文 ──────────
        if (imeVisible || forceToolbarVisible) {
            FormattingToolbar(
                onHideKeyboard = { keyboardController?.hide() },
                onBold = { editor.toggle(RichSpan.Bold) },
                isBoldActive = editor.isActive(RichSpan.Bold),
                onItalic = { editor.toggle(RichSpan.Italic) },
                isItalicActive = editor.isActive(RichSpan.Italic),
                onInsertImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .onGloballyPositioned { toolbarTopWindowY = it.boundsInWindow().top },
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
                onPickDocument = { documentPicker.launch(arrayOf("*/*")) },
                onDismiss = { showAttachSheet = false },
            )
        }

        // 图片预览（全屏覆盖）：左右滑动 / 缩放 / 删除
        previewIndex?.let { idx ->
            val images = editor.blocks.filterIsInstance<ImageBlock>()
            ImagePreviewScreen(
                paths = images.map { it.path },
                initialIndex = idx,
                onDelete = { page ->
                    images.getOrNull(page)?.let { editor.removeBlock(it.id); emitContent() }
                },
                onBack = { previewIndex = null },
            )
        }
    }
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
