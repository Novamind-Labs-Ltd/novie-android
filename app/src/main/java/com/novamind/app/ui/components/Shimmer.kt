package com.novamind.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

private val ShimmerBase = Color(0xFFFFFFFF)
private val ShimmerHighlight = Color(0xFFEDEDED)

/**
 * 给任意元素加上从左到右循环扫过的微光占位效果。
 *
 * 用法：先 `.clip(shape)` 再 `.shimmer()`，扫光会被裁剪在形状内：
 * ```
 * Box(Modifier.size(160.dp, 16.dp).clip(RoundedCornerShape(8.dp)).shimmer())
 * ```
 */
fun Modifier.shimmer(
    base: Color = ShimmerBase,
    highlight: Color = ShimmerHighlight,
    durationMillis: Int = 1300,
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
        ),
        label = "progress",
    )
    drawWithCache {
        val w = size.width
        val band = w * 0.6f
        // 高光带从元素左侧外滑入、右侧外滑出
        val startX = -band + (w + band) * progress
        val brush = Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(startX, 0f),
            end = Offset(startX + band, 0f),
        )
        onDrawBehind { drawRect(brush) }
    }
}

/** 单个圆角占位块（已内置 shimmer），用于固定尺寸的占位（头像圈、胶囊等）。 */
@Composable
fun ShimmerBlock(
    width: Dp,
    height: Dp,
    shape: Shape = RoundedCornerShape(50),
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(width, height)
            .clip(shape)
            .shimmer(),
    )
}
