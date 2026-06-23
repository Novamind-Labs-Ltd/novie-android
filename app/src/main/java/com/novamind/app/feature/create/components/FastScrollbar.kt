package com.novamind.app.feature.create.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import com.novamind.app.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val BarIdle = Color(0x66000000)      // 闲置/可见时的拖杆色（半透明黑）
private val BarActive = ColorPrimary          // 拖拽中的拖杆色（品牌绿）
private val BubbleBg = Color(0xE61A1A1A)

/**
 * 右侧快速拖拽滚动条：覆盖在可滚动内容右缘。
 *
 * - 平时隐藏；滚动或拖拽时淡入，闲置约 1.2s 后淡出。
 * - 拖杆可上下拖动以快速跳转（按 item 比例 [LazyListState.scrollToItem]），拖拽时旁边显示百分比气泡。
 * - 拖杆始终可抓取（即使视觉淡出），但只占据右缘一小条窄区，基本不影响正文点击。
 *
 * @param listState 目标列表状态
 * @param thumbHeight 拖杆高度
 */
@Composable
fun FastScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thumbHeight: Dp = 56.dp,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 内容是否可滚动；不可滚动则完全不显示
    val canScroll = listState.canScrollForward || listState.canScrollBackward
    if (!canScroll) return

    // 滚动进度（0..1）：用「可见 item 平均高度」按像素估算，避免按 index / 首项高度换算
    // 在文本块与 PDF/图片等高矮悬殊的 item 间切换时造成的速度突变（跳变）。
    val progress by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val items = info.visibleItemsInfo
            val total = info.totalItemsCount
            if (total == 0 || items.isEmpty()) return@derivedStateOf 0f
            val avg = items.sumOf { it.size } / items.size.toFloat()
            if (avg <= 0f) return@derivedStateOf 0f
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            val maxScroll = (avg * total - viewport).coerceAtLeast(1f)
            val scrolled = listState.firstVisibleItemIndex * avg + listState.firstVisibleItemScrollOffset
            (scrolled / maxScroll).coerceIn(0f, 1f)
        }
    }

    var dragging by remember { mutableStateOf(false) }

    // 活跃态：滚动中或拖拽中 → 显示；停下 1.2s 后淡出
    val active = listState.isScrollInProgress || dragging
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) {
            visible = true
        } else {
            delay(1200)
            visible = false
        }
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(220), label = "scrollbarAlpha")

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(32.dp),
    ) {
        val trackPx = constraints.maxHeight.toFloat()
        val thumbPx = with(density) { thumbHeight.toPx() }
        val travel = (trackPx - thumbPx).coerceAtLeast(1f)

        // 拖拽中由手势驱动(即时跟手 snap)；跟随滚动时对拖杆位置做轻量弹簧平滑，吸收估算的细小跳变
        var dragTop by remember { mutableFloatStateOf(0f) }
        var scrollJob by remember { mutableStateOf<Job?>(null) }
        val targetTop = if (dragging) dragTop else progress * travel
        val thumbTop by animateFloatAsState(
            targetValue = targetTop,
            animationSpec = if (dragging) snap() else spring(stiffness = Spring.StiffnessMediumLow),
            label = "thumbTop",
        )
        val percent = (((if (dragging) dragTop / travel else progress)) * 100).roundToInt()

        // 拖拽气泡：百分比，置于拖杆左侧、垂直居中
        if (dragging) {
            val bubbleH = 26.dp
            val bubbleHalfPx = with(density) { (bubbleH / 2).toPx() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, (thumbTop + thumbPx / 2f - bubbleHalfPx).roundToInt()) }
                    .padding(end = 16.dp)
                    .height(bubbleH)
                    .clip(RoundedCornerShape(50))
                    .background(BubbleBg)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("$percent%", color = Color.White, fontSize = 12.sp)
            }
        }

        // 拖杆（命中区 32dp 宽 × thumbHeight 高；可见拖杆为右缘细药丸）
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbTop.roundToInt()) }
                .width(32.dp)
                .height(thumbHeight)
                .pointerInput(travel) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragging = true
                            visible = true
                            dragTop = progress * travel
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            dragTop = (dragTop + dy).coerceIn(0f, travel)
                            // 把拖杆位置换算成「索引 + 项内像素偏移」再定位，
                            // 而不是只按整项 index 跳——后者在高矮悬殊(含 PDF)的列表里会一格一格地蹦。
                            val info = listState.layoutInfo
                            val items = info.visibleItemsInfo
                            val total = info.totalItemsCount
                            if (total > 0 && items.isNotEmpty()) {
                                val avg = items.sumOf { it.size } / items.size.toFloat()
                                if (avg > 0f) {
                                    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                                    val maxScroll = (avg * total - viewport).coerceAtLeast(1f)
                                    val targetPx = (dragTop / travel) * maxScroll
                                    val index = (targetPx / avg).toInt().coerceIn(0, total - 1)
                                    val itemOffset = (targetPx - index * avg).toInt().coerceAtLeast(0)
                                    scrollJob?.cancel()
                                    scrollJob = scope.launch { listState.scrollToItem(index, itemOffset) }
                                }
                            }
                        },
                    )
                },
            contentAlignment = Alignment.CenterEnd,
        ) {
            // 可见拖杆：右缘圆角胶囊，中间叠上下箭头作为「可上下拖」的握把提示
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .width(20.dp)
                    .fillMaxHeight()
                    .graphicsLayer { this.alpha = alpha }
                    .clip(RoundedCornerShape(50))
                    .background(if (dragging) BarActive else BarIdle),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_up),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_down),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}
