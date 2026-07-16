package com.novamind.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 自定义下拉刷新容器（不使用 Material3 的 PullToRefreshBox 系统刷新）。
 *
 * 「平级」策略：内容整体随下拉下移，spinner 显示在顶部让出的空白带里，与内容处于同一层级下移，
 * 而非系统那种浮在内容之上的覆盖指示器。适合放在固定头部下方，只对下方内容做下拉刷新。
 *
 * 用法：
 * ```
 * Column {
 *     HomeTopBar(...)                 // 固定头部
 *     AppPullToRefresh(isRefreshing, onRefresh, Modifier.weight(1f)) {
 *         Column(Modifier.verticalScroll(...)) { /* 可滚动内容 */ }
 *     }
 * }
 * ```
 *
 * @param isRefreshing 是否正在刷新（由上层状态驱动）
 * @param onRefresh    越过阈值松手时触发
 * @param threshold    触发刷新的下拉阈值（也是刷新中 spinner 停留处）
 * @param maxDrag      最大可下拉距离（带阻尼）
 */
@Composable
fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    threshold: Dp = 72.dp,
    maxDrag: Dp = 120.dp,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { threshold.toPx() }
    val maxDragPx = with(density) { maxDrag.toPx() }
    val dragMultiplier = 0.5f   // 下拉阻尼

    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }

    // 刷新态变化（且非拖动中）→ 平滑到静止位：刷新中停在阈值，否则收回 0
    LaunchedEffect(isRefreshing) {
        if (!dragging) offset.animateTo(if (isRefreshing) thresholdPx else 0f)
    }

    val connection = remember(isRefreshing, thresholdPx, maxDragPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                // 手指上滑（内容想向上滚）时，先把已下拉的偏移收回（仅手指拖动，惯性 fling 不处理）
                if (source == NestedScrollSource.UserInput && available.y < 0 && offset.value > 0f) {
                    dragging = true
                    val newValue = (offset.value + available.y).coerceAtLeast(0f)
                    val consumed = newValue - offset.value
                    scope.launch { offset.snapTo(newValue) }
                    return androidx.compose.ui.geometry.Offset(0f, consumed)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                // 内容已到顶后仍有向下剩余 → 累积下拉偏移（带阻尼）。
                // 仅响应手指拖动：惯性 fling 的剩余不应把指示器顶出来，否则快速下滑后不回弹。
                if (source == NestedScrollSource.UserInput && available.y > 0 && !isRefreshing) {
                    dragging = true
                    val newValue = (offset.value + available.y * dragMultiplier).coerceIn(0f, maxDragPx)
                    scope.launch { offset.snapTo(newValue) }
                    return androidx.compose.ui.geometry.Offset(0f, available.y)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                dragging = false
                if (!isRefreshing && offset.value >= thresholdPx) {
                    onRefresh()
                    offset.animateTo(thresholdPx)
                } else if (!isRefreshing) {
                    offset.animateTo(0f)
                }
                return Velocity.Zero
            }
        }
    }

    Box(modifier.nestedScroll(connection)) {
        // 顶部 spinner：位于内容让出的空白带中央，随偏移淡入
        val fraction = (offset.value / thresholdPx).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = (offset.value / 2f)
                    alpha = fraction
                },
            contentAlignment = Alignment.Center,
        ) {
            AppPullRefreshIndicator(isRefreshing = isRefreshing, fraction = fraction)
        }

        // 内容整体随下拉下移
        Box(
            modifier = Modifier.graphicsLayer { translationY = offset.value },
        ) {
            content()
        }
    }
}
