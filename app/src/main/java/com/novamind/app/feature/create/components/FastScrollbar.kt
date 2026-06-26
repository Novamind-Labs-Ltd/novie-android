package com.novamind.app.feature.create.components
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.colors.IconColors

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

    // 累计高度模型：缓存每个 item 滚动经过时测到的真实高度（越滚越全）。
    // 总高 = 各项高度之和（未知项用已知平均补），位置 = 前缀项高度之和 + 项内偏移。
    // 相比「瞬时可见平均」，PDF/图片等超高 item 进出视口时总高估计稳定、不突变 → 不跳。
    val heights = remember(listState) { mutableMapOf<Int, Int>() }

    fun avgItem(): Float =
        if (heights.isEmpty()) 1f else heights.values.sum().toFloat() / heights.size

    fun heightAt(index: Int, avg: Float): Float = heights[index]?.toFloat() ?: avg

    // [0, count) 项的累计高度
    fun cumulativeBefore(count: Int, avg: Float): Float {
        var sum = 0f
        for (i in 0 until count) sum += heightAt(i, avg)
        return sum
    }

    // 滚动进度（0..1）
    val progress by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0 || info.visibleItemsInfo.isEmpty()) return@derivedStateOf 0f
            info.visibleItemsInfo.forEach { heights[it.index] = it.size }
            val avg = avgItem()
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            val maxScroll = (cumulativeBefore(total, avg) - viewport).coerceAtLeast(1f)
            val scrolled = cumulativeBefore(listState.firstVisibleItemIndex, avg) +
                listState.firstVisibleItemScrollOffset
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
                            // 用累计高度模型把拖杆位置反解为「目标项 + 项内偏移」，与进度同一套估算，
                            // 拖杆与内容同步、且不随可见集变化突变。
                            val info = listState.layoutInfo
                            val total = info.totalItemsCount
                            if (total > 0 && info.visibleItemsInfo.isNotEmpty()) {
                                info.visibleItemsInfo.forEach { heights[it.index] = it.size }
                                val avg = avgItem()
                                val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                                val maxScroll = (cumulativeBefore(total, avg) - viewport).coerceAtLeast(1f)
                                val targetPx = (dragTop / travel) * maxScroll
                                // 沿累计高度找到目标项与项内偏移
                                var acc = 0f
                                var index = 0
                                while (index < total - 1) {
                                    val h = heightAt(index, avg)
                                    if (acc + h > targetPx) break
                                    acc += h
                                    index++
                                }
                                val itemOffset = (targetPx - acc).toInt().coerceAtLeast(0)
                                scrollJob?.cancel()
                                scrollJob = scope.launch { listState.scrollToItem(index, itemOffset) }
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
                    .background(if (dragging) IconColors.Brand.default.current() else BarIdle),
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
