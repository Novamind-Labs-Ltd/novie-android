package com.novamind.app.feature.create

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
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
import com.novamind.app.feature.create.components.TagPickerSheet
import com.novamind.app.feature.create.editor.RichSpan
import com.novamind.app.feature.create.editor.RichTextState
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
        modifier = modifier,
    )
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun CreateScreen(
    uiState: CreateUiState,
    onEvent: (CreateEvent) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    forceToolbarVisible: Boolean = false,   // 预览用：强制显示格式工具栏
) {
    val imeVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    val timeLabel = remember { DateFormat.format("Today HH:mm", Date()).toString() }

    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    // 正文富文本状态（加粗等格式），纯文本与 ViewModel 同步
    val bodyState = remember { RichTextState(uiState.body) }
    // 外部纯文本变化（加载笔记 / 撤销重做）时回填，避免与本地输入互相覆盖
    LaunchedEffect(uiState.body) {
        if (uiState.body != bodyState.plainText) bodyState.setPlainText(uiState.body)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgPage)
                .statusBarsPadding()
                .navigationBarsPadding()
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

            // ── 正文 ──────────────────────────────────────────────────────
            BasicTextField(
                value = bodyState.value,
                onValueChange = {
                    bodyState.onValueChange(it)
                    onEvent(CreateEvent.BodyChanged(it.text))
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    color = ColorTextTitle,
                    lineHeight = 26.sp,
                ),
                cursorBrush = SolidColor(ColorTextTitle),
                decorationBox = { inner ->
                    if (bodyState.value.text.isEmpty()) {
                        Text("Type here...", fontSize = 16.sp, color = ColorTextHint)
                    }
                    inner()
                },
            )

            // ── 格式工具栏 ────────────────────────────────────────────────
            if (imeVisible || forceToolbarVisible) {
                FormattingToolbar(
                    onHideKeyboard = { keyboardController?.hide() },
                    onBold = { bodyState.toggle(RichSpan.Bold) },
                    isBoldActive = bodyState.isActive(RichSpan.Bold),
                    onItalic = { bodyState.toggle(RichSpan.Italic) },
                    isItalicActive = bodyState.isActive(RichSpan.Italic),
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
