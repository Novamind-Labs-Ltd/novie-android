package com.novamind.app.feature.create.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.feature.create.editor.ImageBlock
import com.novamind.app.feature.create.editor.NoteEditorState
import com.novamind.app.feature.create.editor.TextBlock
import java.io.File

// 光标与可见下界之间的安全边距
private val REVEAL_MARGIN = 16.dp

/**
 * 图文正文编辑器（Block-editor 方案 Phase 1）。
 *
 * 键盘方案：配合 `adjustNothing`（见 MainActivity）——键盘弹出窗口不重排、内容/光标布局不动；
 * 键盘只是「盖」在底部。本编辑器在「光标底超过可见下界（视口底 − 键盘 − 工具栏 − 边距）」时，
 * 才用同帧滚动恰好把光标露出；其余时刻不动 → 持续输入光标位置不变、内容仅在被遮时上滚。
 */
@Composable
fun NoteContentEditor(
    state: NoteEditorState,
    onContentChanged: () -> Unit,
    toolbarHeightPx: Int = 0,   // 悬浮工具栏实测高度（键盘隐藏时由调用方传 0）
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    var contentCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // 键盘高度（adjustNothing 下窗口不缩，但 ime inset 仍上报）
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val toolbarReservePx = if (imeBottomPx > 0) toolbarHeightPx.toFloat() else 0f
    val revealMarginPx = with(density) { REVEAL_MARGIN.toPx() }
    // 可见区下界以下被键盘/工具栏遮挡的总高度
    val bottomCoverPx = imeBottomPx + toolbarReservePx + revealMarginPx
    // 滚动内容底部预留：让末尾内容能滚到键盘/工具栏之上
    val bottomPad = with(density) { (imeBottomPx + toolbarReservePx).toDp() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .onGloballyPositioned { contentCoords = it }
            // 点击正文空白处：聚焦最后一个文本块并调起键盘
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    state.focusLastTextBlock()
                    keyboard?.show()
                },
            ),
    ) {
        val singleEmpty = state.blocks.size == 1 &&
            (state.blocks.first() as? TextBlock)?.rich?.plainText?.isEmpty() == true

        state.blocks.forEach { block ->
            key(block.id) {
                when (block) {
                    is TextBlock -> TextBlockField(
                        block = block,
                        showPlaceholder = singleEmpty,
                        onFocused = { state.onTextFocused(block.id) },
                        onChanged = onContentChanged,
                        scrollState = scrollState,
                        contentCoordsProvider = { contentCoords },
                        bottomCoverPx = bottomCoverPx,
                    )

                    is ImageBlock -> ImageBlockView(
                        block = block,
                        onDelete = {
                            state.removeImage(block.id)
                            onContentChanged()
                        },
                    )
                }
            }
        }

        // 导航栏清空白
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        // 键盘/工具栏预留：使末尾内容/光标能滚到它们之上
        Spacer(modifier = Modifier.height(bottomPad))
    }
}

@Composable
private fun TextBlockField(
    block: TextBlock,
    showPlaceholder: Boolean,
    onFocused: () -> Unit,
    onChanged: () -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    contentCoordsProvider: () -> LayoutCoordinates?,
    bottomCoverPx: Float,
) {
    var fieldCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var isFocused by remember { mutableStateOf(false) }
    // 上一次光标所在的视觉行号；仅当行号变化（换行/折行/上移）时才考虑滚动，
    // 同一行内连续打字行号不变 → 不滚动。
    var lastCursorLine by remember { mutableStateOf(-1) }

    BasicTextField(
        value = block.rich.value,
        onValueChange = {
            block.rich.onValueChange(it)
            onChanged()
        },
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { fieldCoords = it }
            .focusRequester(block.focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .padding(horizontal = 20.dp, vertical = 6.dp),
        textStyle = TextStyle(
            fontSize = 16.sp,
            color = ColorTextTitle,
            lineHeight = 26.sp,
        ),
        cursorBrush = SolidColor(ColorTextTitle),
        onTextLayout = { layout ->
            val content = contentCoordsProvider()
            val field = fieldCoords
            if (isFocused && content != null && field != null && content.isAttached && field.isAttached) {
                val value = block.rich.value
                val offset = value.selection.end.coerceIn(0, value.text.length)
                val line = layout.getLineForOffset(offset)
                // 仅当光标所在视觉行变化时才评估滚动；同一行内打字（行号不变）直接跳过
                if (line != lastCursorLine) {
                    lastCursorLine = line
                    val rect = layout.getCursorRect(offset)
                    val viewport = content.size.height
                    if (viewport > 0) {
                        val cursorBottomViewportY =
                            content.localPositionOf(field, Offset(0f, rect.bottom)).y
                        val cursorTopViewportY =
                            content.localPositionOf(field, Offset(0f, rect.top)).y
                        // 可见下界 = 视口底 − 键盘 − 工具栏 − 安全边距
                        val visibleBottom = viewport - bottomCoverPx
                        when {
                            // 新行被键盘/工具栏遮住 → 同帧上滚恰好露出
                            cursorBottomViewportY > visibleBottom ->
                                scrollState.dispatchRawDelta(cursorBottomViewportY - visibleBottom)
                            // 光标在可视区上方 → 向上滚动露出
                            cursorTopViewportY < 0f ->
                                scrollState.dispatchRawDelta(cursorTopViewportY)
                        }
                    }
                }
            }
        },
        decorationBox = { inner ->
            if (showPlaceholder && block.rich.value.text.isEmpty()) {
                Text("Type here...", fontSize = 16.sp, color = ColorTextHint)
            }
            inner()
        },
    )
}

@Composable
private fun ImageBlockView(
    block: ImageBlock,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        AsyncImage(
            model = File(block.path),
            contentDescription = "Note image",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE8E7E2)),
        )
        // 删除按钮
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0x99000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    onClick = onDelete,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", color = Color.White, fontSize = 18.sp)
        }
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFFDFCF8)
@Composable
private fun NoteContentEditorPreview() {
    val editor = remember {
        NoteEditorState().apply {
            loadDocument(
                json = null,
                fallbackPlain = "买菜清单\n\n· 西红柿\n· 鸡蛋\n· 一袋米\n\n晚上记得回个电话。",
            )
        }
    }
    AppTheme {
        NoteContentEditor(
            state = editor,
            onContentChanged = {},
            toolbarHeightPx = 0,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
