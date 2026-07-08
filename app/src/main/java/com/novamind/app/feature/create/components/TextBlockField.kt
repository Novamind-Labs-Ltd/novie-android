package com.novamind.app.feature.create.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import com.novamind.app.common.config.AppConfig
import com.novamind.app.feature.create.editor.TextBlock
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import androidx.compose.material3.Text
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

// 选区骨架扫光条配色（不透明，盖住原文字）
private val PolishBarBase = Color(AppConfig.Polish.BAR_BASE)
private val PolishBarHighlight = Color(AppConfig.Polish.BAR_HIGHLIGHT)

/**
 * 单个文本块的可编辑富文本框（库 [BasicRichTextEditor]）。除编辑外还负责：
 * 光标随键盘/工具栏遮挡自动滚入可见区、AI 骨架扫光占位、空块占位符、字数上限。
 */
@Composable
internal fun TextBlockField(
    block: TextBlock,
    showPlaceholder: Boolean,
    readOnly: Boolean,
    onFocused: () -> Unit,
    onChanged: () -> Unit,
    lazyListState: LazyListState,
    contentCoordsProvider: () -> LayoutCoordinates?,
    coverTopProvider: () -> Float,
    revealMarginPx: Float,
    onRegisterReveal: ((() -> Unit)?) -> Unit,
    maxBlockLen: Int = Int.MAX_VALUE,
    polishRange: IntRange? = null,
) {
    val scope = rememberCoroutineScope()
    var fieldCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var isFocused by remember { mutableStateOf(false) }
    var latestLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // 选区骨架：在选中字符 bounds 上循环扫光（覆盖原文字，模拟该段正在重写）
    val polishTransition = rememberInfiniteTransition(label = "polish")
    val polishProgress by polishTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1300, easing = LinearEasing)),
        label = "polishProgress",
    )
    // 上次光标视觉行号；同一行连续打字不滚动，仅行号变化时才评估
    var lastCursorLine by remember { mutableStateOf(-1) }

    // 把光标滚到可见区。respectLineGate=true 仅在行号变化时滚（打字/换行）；
    // false 无条件评估（键盘弹出：行号没变但被键盘盖住也要滚）。
    fun revealCursor(respectLineGate: Boolean) {
        val content = contentCoordsProvider() ?: return
        val field = fieldCoords ?: return
        val layout = latestLayout ?: return
        if (!content.isAttached || !field.isAttached) return
        // 以已测量 layout 的字符数兜底：文本可能比 layout 更长，
        // 直接用文本长度会让 getCursorRect/getLineForOffset 越界崩溃。
        val textLen = block.rich.annotatedString.text.length
        val maxOffset = minOf(textLen, layout.layoutInput.text.length)
        val offset = block.rich.selection.end.coerceIn(0, maxOffset)
        val line = layout.getLineForOffset(offset)
        if (respectLineGate && line == lastCursorLine) return
        lastCursorLine = line
        val viewport = content.size.height
        if (viewport <= 0) return
        val rect = layout.getCursorRect(offset)
        val cursorBottomViewportY = content.localPositionOf(field, Offset(0f, rect.bottom)).y
        val cursorTopViewportY = content.localPositionOf(field, Offset(0f, rect.top)).y
        // 可见下界 = 工具栏真实顶边（换算到本视口坐标）− 安全边距；
        // 无遮挡（键盘收起）时退化为视口底 − 边距
        val cover = coverTopProvider()
        val coverTopLocal =
            if (cover == Float.MAX_VALUE) viewport.toFloat()
            else content.windowToLocal(Offset(0f, cover)).y
        val visibleBottom = coverTopLocal.coerceAtMost(viewport.toFloat()) - revealMarginPx
        // 滚动放协程里推迟到布局帧之外：revealCursor 可能在 onTextLayout（测量阶段）
        // 被调用，同步 scrollBy 会触发重测量而崩溃（performMeasureAndLayout during layout）。
        val delta = when {
            // 光标底被键盘/工具栏遮住 → 上滚恰好露出
            cursorBottomViewportY > visibleBottom -> cursorBottomViewportY - visibleBottom
            // 光标在可视区上方 → 向下露出（向上滚动内容）
            cursorTopViewportY < 0f -> cursorTopViewportY
            else -> 0f
        }
        if (delta != 0f) {
            scope.launch { lazyListState.scrollBy(delta) }
        }
    }

    // 库 RichTextState 自行管理输入，用 snapshotFlow 观察文本变化触发保存（drop(1) 跳过首帧初值）
    LaunchedEffect(block) {
        snapshotFlow { block.rich.annotatedString }
            .drop(1)
            .collect { onChanged() }
    }

    BasicRichTextEditor(
        state = block.rich,
        readOnly = readOnly,   // 录音期间只读：不可输入、点击不弹软键盘
        maxLength = maxBlockLen,   // 超过本块上限的输入被库忽略
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { fieldCoords = it }
            .focusRequester(block.focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                    // 登记本块的 reveal：键盘弹出时由父组件调用（忽略行号门限）
                    onRegisterReveal { revealCursor(respectLineGate = false) }
                }
            }
            .padding(horizontal = 20.dp, vertical = 6.dp)
            // 骨架扫光叠在 padding 之后，坐标系与文本布局一致
            .drawWithContent {
                drawContent()
                val layout = latestLayout
                val range = polishRange
                if (layout != null && range != null && !range.isEmpty()) {
                    // 以已测量 layout 的字符数为界，防止与更新更快的文本不同步而越界
                    val textLen = layout.layoutInput.text.length
                    val start = range.first.coerceIn(0, textLen)
                    val end = (range.last + 1).coerceIn(0, textLen)
                    if (end > start) {
                        val band = size.width * 0.6f
                        val sweepX = -band + (size.width + band) * polishProgress
                        val brush = Brush.linearGradient(
                            colors = listOf(PolishBarBase, PolishBarHighlight, PolishBarBase),
                            start = Offset(sweepX, 0f),
                            end = Offset(sweepX + band, 0f),
                        )
                        // getPathForRange 返回整段选区（跨所有行）的路径，一次性覆盖全部选中文字
                        val path = layout.getPathForRange(start, end)
                        drawPath(path = path, brush = brush)
                    }
                }
            },
        textStyle = TextStyle(
            fontSize = 16.sp,
            color = TextColors.Primary.default.current(),
            lineHeight = 26.sp,
        ),
        cursorBrush = SolidColor(TextColors.Primary.default.current()),
        onTextLayout = { layout ->
            latestLayout = layout
            if (isFocused) revealCursor(respectLineGate = true)
        },
        decorationBox = { inner ->
            if (showPlaceholder && block.rich.annotatedString.text.isEmpty()) {
                Text("Type here...", fontSize = 16.sp, color = TextColors.Primary.tertiary.current())
            }
            inner()
        },
    )
}

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Create · TextBlockField（有内容）")
@Composable
private fun TextBlockFieldPreview() {
    AppTheme {
        TextBlockField(
            block = TextBlock(initialText = "示例正文：随手记一笔。"),
            showPlaceholder = false,
            readOnly = false,
            onFocused = {},
            onChanged = {},
            lazyListState = rememberLazyListState(),
            contentCoordsProvider = { null },
            coverTopProvider = { Float.MAX_VALUE },
            revealMarginPx = 0f,
            onRegisterReveal = {},
        )
    }
}

@Preview(showBackground = true, name = "Create · TextBlockField（空 + 占位符）")
@Composable
private fun TextBlockFieldEmptyPreview() {
    AppTheme {
        TextBlockField(
            block = TextBlock(),
            showPlaceholder = true,
            readOnly = false,
            onFocused = {},
            onChanged = {},
            lazyListState = rememberLazyListState(),
            contentCoordsProvider = { null },
            coverTopProvider = { Float.MAX_VALUE },
            revealMarginPx = 0f,
            onRegisterReveal = {},
        )
    }
}
