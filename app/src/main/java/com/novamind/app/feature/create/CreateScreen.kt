package com.novamind.app.feature.create

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
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
import com.novamind.app.feature.create.components.CreateTopBar
import com.novamind.app.feature.create.components.FolderPickerSheet
import com.novamind.app.feature.create.components.FormattingToolbar
import com.novamind.app.feature.create.components.NoteContentEditor
import com.novamind.app.feature.create.components.TagPickerSheet
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
    val imeVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    val timeLabel = remember { DateFormat.format("Today HH:mm", Date()).toString() }

    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
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
        if (autoFocusBody) editor.requestInitialFocus()
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

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgPage)
                .statusBarsPadding()
                // 仅处理键盘 inset；导航栏间距放到正文滚动内容末尾（见 NoteContentEditor），
                // 避免与键盘 inset 叠加产生多余间距
                .imePadding(),
        ) {
            // ── 顶部操作行 ────────────────────────────────────────────────
            CreateTopBar(
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                onBack = {
                    keyboardController?.hide()
                    onEvent(CreateEvent.SaveNote)
                },
                onShare = {},
                onUndo = { onEvent(CreateEvent.UndoEdit) },
                onRedo = { onEvent(CreateEvent.RedoEdit) },
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
                        Text("New note", fontSize = 15.sp, fontWeight = FontWeight.Normal, color = ColorTextHint)
                    }
                    inner()
                },
            )

            // ── Meta 操作行 ───────────────────────────────────────────────
            CreateMetaRow(
                selectedFolder = uiState.selectedFolder,
                selectedTags = uiState.selectedTags,
                timeLabel = timeLabel,
                onShowFolderPicker = { onEvent(CreateEvent.ShowFolderPicker) },
                onShowTagPicker = { onEvent(CreateEvent.ShowTagPicker) },
            )

            // ── 正文（图文混排） ──────────────────────────────────────────
            NoteContentEditor(
                state = editor,
                onContentChanged = emitContent,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            // ── 格式工具栏 ────────────────────────────────────────────────
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
                onDismiss = { onEvent(CreateEvent.DismissFolderPicker) },
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
