package com.novamind.app.feature.create.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.feature.create.editor.FileBlock
import com.novamind.app.feature.create.editor.ImageBlock
import com.novamind.app.feature.create.editor.MarkdownBlock
import com.novamind.app.feature.create.editor.NoteEditorState
import com.novamind.app.feature.create.editor.PdfBlock
import com.novamind.app.feature.create.editor.TextBlock

// 光标与可见下界之间的安全边距（越大，光标停得离工具栏越远 / 越高）
private val REVEAL_MARGIN = 8.dp

/**
 * 图文正文编辑器（Block-editor 方案 Phase 1）。
 *
 * 键盘方案：配合 `adjustNothing`（见 MainActivity）——键盘弹出窗口不重排、内容/光标布局不动；
 * 键盘只是「盖」在底部。本编辑器以「悬浮工具栏的真实顶边」为遮挡线（[coverTopWindowY]，窗口坐标），
 * 在「光标底低于该顶边 − 安全边距」时才同帧上滚恰好露出；其余时刻不动。
 */
@Composable
fun NoteContentEditor(
    state: NoteEditorState,
    onContentChanged: () -> Unit,
    coverTopWindowY: Float = Float.MAX_VALUE,   // 工具栏顶边窗口 Y；无遮挡时传 MAX_VALUE
    onImageClick: (String) -> Unit = {},        // 点击图片块（传块 id）→ 进入预览
    header: (@Composable () -> Unit)? = null,   // 随正文一起滚动的头部（标题 / folder / tags 等）
    readOnly: Boolean = false,                  // 录音期间等场景：正文不可编辑、点击不弹键盘
    bodyCharLimit: Int = Int.MAX_VALUE,         // 正文可输入字数上限（= 总上限 − 标题字数）
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    var contentCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // 键盘高度（adjustNothing 下窗口不缩，但 ime inset 仍上报）
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val revealMarginPx = with(density) { REVEAL_MARGIN.toPx() }
    // 滚动内容底部预留：键盘 + 工具栏约一行 + 边距，保证末尾内容能滚到工具栏之上
    val bottomPad = with(density) { imeBottomPx.toDp() } + 96.dp

    // 始终读到最新的遮挡线（供已注册的 reveal 闭包使用，避免捕获到旧值）
    val coverTopState = rememberUpdatedState(coverTopWindowY)
    // 当前焦点文本块登记的「把光标滚到可见」回调
    var revealFocused by remember { mutableStateOf<(() -> Unit)?>(null) }
    // 键盘弹出 / 工具栏顶边变化时（遮挡线有效），主动把焦点光标滚到可见
    LaunchedEffect(coverTopWindowY) {
        if (coverTopWindowY != Float.MAX_VALUE) revealFocused?.invoke()
    }

    val singleEmpty = state.blocks.size == 1 &&
        (state.blocks.first() as? TextBlock)?.rich?.annotatedString?.text?.isEmpty() == true

    // 懒加载正文：只渲染可见(及邻近)块，离屏块不渲染/不解码图片/不渲 PDF/MD。
    Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { contentCoords = it },
    ) {
        // 头部（标题 / folder / tags 等）：作为首个 item，随正文一起滚动
        if (header != null) {
            item(key = "__header__") { header() }
        }

        items(state.blocks, key = { it.id }) { block ->
            when (block) {
                is TextBlock -> TextBlockField(
                    block = block,
                    showPlaceholder = singleEmpty,
                    readOnly = readOnly,
                    onFocused = { state.onTextFocused(block.id) },
                    onChanged = onContentChanged,
                    lazyListState = listState,
                    contentCoordsProvider = { contentCoords },
                    coverTopProvider = { coverTopState.value },
                    revealMarginPx = revealMarginPx,
                    onRegisterReveal = { revealFocused = it },
                    // 本块可输入上限 = 正文上限 − 其他文本块已用字数
                    maxBlockLen = (bodyCharLimit - (state.textLength - block.rich.annotatedString.text.length))
                        .coerceAtLeast(0),
                    polishRange = when {
                        // 全部文字模式：每个文本块都整段做骨架
                        state.polishAll -> 0 until block.rich.annotatedString.text.length
                        // 选区模式：仅命中的文本块按选区做骨架
                        state.polishTarget?.blockId == block.id ->
                            state.polishTarget!!.start until state.polishTarget!!.end
                        else -> null
                    },
                )

                is ImageBlock -> ImageBlockView(
                    block = block,
                    onClick = { onImageClick(block.id) },
                    onDelete = {
                        state.removeBlock(block.id)
                        onContentChanged()
                    },
                )

                is FileBlock -> FileBlockView(
                    block = block,
                    onDelete = {
                        state.removeBlock(block.id)
                        onContentChanged()
                    },
                )

                is MarkdownBlock -> MarkdownBlockView(
                    block = block,
                    readOnly = readOnly,
                    onContentChange = {
                        state.updateMarkdown(block.id, it)
                        onContentChanged()
                    },
                    onDelete = {
                        state.removeBlock(block.id)
                        onContentChanged()
                    },
                )

                is PdfBlock -> PdfBlockView(
                    block = block,
                    onDelete = {
                        state.removeBlock(block.id)
                        onContentChanged()
                    },
                )
            }
        }

        // 末尾：导航栏 + 键盘/工具栏留白，且作为「点击空白聚焦末尾文本块」的热区
        item(key = "__tail__") {
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomPad)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !readOnly,   // 录音期间禁用点击空白聚焦/弹键盘
                        onClick = {
                            state.focusLastTextBlock()
                            keyboard?.show()
                        },
                    ),
            )
        }
    }

        // 右侧快速拖拽滚动条（覆盖在正文右缘）
        FastScrollbar(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
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
            modifier = Modifier.fillMaxSize(),
        )
    }
}
