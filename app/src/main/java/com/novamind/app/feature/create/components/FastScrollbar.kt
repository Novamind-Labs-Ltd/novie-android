package com.novamind.app.feature.create.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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

    // 滚动进度（0..1）：变高 item 用「首个可见 index + 其内偏移」近似
    val progress by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf 0f
            val denom = (total - info.visibleItemsInfo.size).coerceAtLeast(1)
            val firstSize = info.visibleItemsInfo.firstOrNull()?.size ?: 1
            val offFrac = listState.firstVisibleItemScrollOffset.toFloat() / firstSize.coerceAtLeast(1)
            ((listState.firstVisibleItemIndex + offFrac) / denom).coerceIn(0f, 1f)
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

        // 拖拽中由手势驱动；否则跟随滚动进度
        var dragTop by remember { mutableFloatStateOf(0f) }
        val thumbTop = if (dragging) dragTop else progress * travel
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
                            val total = listState.layoutInfo.totalItemsCount
                            if (total > 0) {
                                val target = ((dragTop / travel) * (total - 1)).roundToInt()
                                    .coerceIn(0, total - 1)
                                scope.launch { listState.scrollToItem(target) }
                            }
                        },
                    )
                },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .width(5.dp)
                    .fillMaxHeight()
                    .graphicsLayer { this.alpha = alpha }
                    .clip(RoundedCornerShape(50))
                    .background(if (dragging) BarActive else BarIdle),
            )
        }
    }
}
