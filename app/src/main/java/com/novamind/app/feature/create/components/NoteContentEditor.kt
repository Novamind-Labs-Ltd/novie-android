package com.novamind.app.feature.create.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.mikepenz.markdown.m3.Markdown
import com.novamind.app.common.config.AppConfig
import com.novamind.app.common.pdf.PdfRenderSession
import com.novamind.app.common.pdf.PdfReader
import com.novamind.app.common.pdf.PdfViewerActivity
import com.novamind.app.ui.theme.AppTheme
import androidx.compose.ui.res.painterResource
import com.novamind.app.R
import com.novamind.app.feature.create.editor.FileBlock
import com.novamind.app.feature.create.editor.ImageBlock
import com.novamind.app.feature.create.editor.MarkdownBlock
import com.novamind.app.feature.create.editor.NoteEditorState
import com.novamind.app.feature.create.editor.PdfBlock
import com.novamind.app.feature.create.editor.TextBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
        (state.blocks.first() as? TextBlock)?.rich?.plainText?.isEmpty() == true

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
                    polishRange = when {
                        // 全部文字模式：每个文本块都整段做骨架
                        state.polishAll -> 0 until block.rich.value.text.length
                        // 选区模式：仅命中的文本块按选区做骨架
                        state.polishTarget?.blockId == block.id ->
                            state.polishTarget!!.start until state.polishTarget!!.end
                        else -> null
                    },
                )

                is ImageBlock -> ImageBlockView(
                    block = block,
                    onClick = { onImageClick(block.id) },
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

// 选区骨架扫光条配色（不透明，盖住原文字）；取值集中在 AppConfig.Polish
private val PolishBarBase = Color(AppConfig.Polish.BAR_BASE)
private val PolishBarHighlight = Color(AppConfig.Polish.BAR_HIGHLIGHT)

@Composable
private fun TextBlockField(
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
    // 上一次光标所在的视觉行号；仅当行号变化（换行/折行/上移）时才考虑滚动，
    // 同一行内连续打字行号不变 → 不滚动。
    var lastCursorLine by remember { mutableStateOf(-1) }

    // 把光标滚到可见区。respectLineGate=true 时仅在「光标视觉行变化」才滚（打字/换行场景）；
    // false 时无条件评估（键盘弹出场景，行号没变但被键盘盖住，也要滚）。
    fun revealCursor(respectLineGate: Boolean) {
        val content = contentCoordsProvider() ?: return
        val field = fieldCoords ?: return
        val layout = latestLayout ?: return
        if (!content.isAttached || !field.isAttached) return
        val value = block.rich.value
        val offset = value.selection.end.coerceIn(0, value.text.length)
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
        // 注意：revealCursor 可能在 onTextLayout（测量/布局阶段）被调用，
        // 此时不能同步触发滚动——LazyListState 的滚动会强制重新测量，导致
        // "performMeasureAndLayout called during measure layout" 崩溃。
        // 因此用协程把滚动推迟到当前布局帧之外执行（scrollBy 为即时非动画滚动）。
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

    BasicTextField(
        value = block.rich.value,
        onValueChange = {
            block.rich.onValueChange(it)
            onChanged()
        },
        readOnly = readOnly,   // 录音期间只读：不可输入、点击不弹软键盘
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
            // 选区骨架占位：drawWithContent 在 padding 之后，坐标系与文本布局一致
            .drawWithContent {
                drawContent()
                val layout = latestLayout
                val range = polishRange
                if (layout != null && range != null && !range.isEmpty()) {
                    val textLen = block.rich.value.text.length
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
            color = ColorTextTitle,
            lineHeight = 26.sp,
        ),
        cursorBrush = SolidColor(ColorTextTitle),
        onTextLayout = { layout ->
            latestLayout = layout
            if (isFocused) revealCursor(respectLineGate = true)
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
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        // 点击图片进入预览（删除在预览页内进行）
        AsyncImage(
            model = File(block.path),
            contentDescription = "Note image",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE8E7E2))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onClick,
                ),
        )
    }
}

@Composable
private fun FileBlockView(
    block: FileBlock,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        // 文件 chip：文档图标 + 文件名
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFFFFFF),
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_document),
                    contentDescription = null,
                    tint = ColorTextTitle,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = block.name,
                    fontSize = 15.sp,
                    color = ColorTextTitle,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 删除
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false),
                            onClick = onDelete,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("×", color = ColorTextSub, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun MarkdownBlockView(
    block: MarkdownBlock,
    readOnly: Boolean,
    onContentChange: (String) -> Unit,
    onDelete: () -> Unit,
) {
    // 编辑/预览切换；编辑时直接修改原始 markdown 文本，退出编辑回到渲染视图
    var editing by remember(block.id) { mutableStateOf(false) }
    var draft by remember(block.id) { mutableStateOf(block.content) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFFFFFF),
            shadowElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // 头部：标识 + 编辑/完成 + 删除
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Markdown", fontSize = 12.sp, color = ColorTextSub)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // 录音等只读场景下隐藏「编辑」入口
                        if (!readOnly) {
                            Text(
                                text = if (editing) "完成" else "编辑",
                                fontSize = 13.sp,
                                color = ColorPrimary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        onClick = {
                                            if (editing) onContentChange(draft)
                                            editing = !editing
                                        },
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = false),
                                    onClick = onDelete,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("×", color = ColorTextSub, fontSize = 18.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (editing) {
                    // 编辑原始 markdown 文本（等宽字体），实时写回块内容
                    BasicTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            onContentChange(it)
                        },
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = ColorTextTitle,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 22.sp,
                        ),
                        cursorBrush = SolidColor(ColorPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF3F3F0))
                            .padding(12.dp),
                    )
                } else {
                    // 渲染视图（m3 变体，跟随 Material3 主题）
                    Markdown(content = block.content)
                }
            }
        }
    }
}

@Composable
private fun PdfBlockView(
    block: PdfBlock,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val targetWidth = context.resources.displayMetrics.widthPixels
    // 内联整合 PDF 阅读器：开一个常驻 session（按需渲染 + LRU 缓存，见 PdfReader）。
    // 块滚出屏幕时(LazyColumn 懒加载)整体被 dispose，关闭 session 释放内存。
    var session by remember(block.path) { mutableStateOf<PdfRenderSession?>(null) }
    var pageCount by remember(block.path) { mutableStateOf(0) }
    var aspect by remember(block.path) { mutableStateOf(0.707f) }
    var loading by remember(block.path) { mutableStateOf(true) }

    LaunchedEffect(block.path) {
        loading = true
        val s = withContext(Dispatchers.IO) { PdfRenderSession.open(File(block.path), targetWidth) }
        if (s != null) {
            session = s
            pageCount = s.pageCount
            aspect = s.firstPageAspect
        }
        loading = false
    }
    // 块离开组合（滚出屏幕 / 删除）时关闭 session
    DisposableEffect(block.path) {
        onDispose { session?.close() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFFFFFF),
            shadowElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // 头部：文件名 + 删除
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_document),
                            contentDescription = null,
                            tint = ColorTextSub,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = block.name,
                            fontSize = 13.sp,
                            color = ColorTextSub,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false),
                                onClick = onDelete,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", color = ColorTextSub, fontSize = 18.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                val s = session
                when {
                    loading -> Text("正在渲染 PDF…", fontSize = 13.sp, color = ColorTextHint)
                    s == null -> Text("无法渲染该 PDF", fontSize = 13.sp, color = ColorTextHint)
                    else -> {
                        // 内联阅读器：宽度撑满，高度按首页宽高比，封顶屏幕 70%
                        val maxH = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val h = (maxWidth / aspect.coerceAtLeast(0.1f)).coerceAtMost(maxH)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(h),
                            ) {
                                PdfReader(
                                    session = s,
                                    pageCount = pageCount,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFE8E7E2)),
                                )
                                // 右上角：进入全屏阅读器
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x66000000))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(bounded = false),
                                            onClick = { PdfViewerActivity.start(context, block.path) },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_fullscreen),
                                        contentDescription = "全屏阅读",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "共 $pageCount 页 · 左右翻页 · 捏合/双击缩放 · 右上角全屏",
                            fontSize = 11.sp,
                            color = ColorTextHint,
                        )
                    }
                }
            }
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
            modifier = Modifier.fillMaxSize(),
        )
    }
}
