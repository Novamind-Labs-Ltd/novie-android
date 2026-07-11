package com.novamind.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

/**
 * 给任意元素加上从左到右循环扫过的微光占位效果。
 *
 * 配色：默认引用 ui/colors 设计系统令牌（底=Surface.default、高光=Primary.tertiary），
 * 随主题深浅自动解析。`Modifier.shimmer` 为普通函数，默认参数无法调 @Composable
 * 解析，故参数可空、在 composed 块内取令牌。
 *
 * 用法：先 `.clip(shape)` 再 `.shimmer()`，扫光会被裁剪在形状内：
 * ```
 * Box(Modifier.size(160.dp, 16.dp).clip(RoundedCornerShape(8.dp)).shimmer())
 * ```
 */
fun Modifier.shimmer(
    base: Color? = null,
    highlight: Color? = null,
    durationMillis: Int = 1300,
): Modifier = composed {
    val baseColor = base ?: BackgroundColors.Surface.default.current()
    val highlightColor = highlight ?: BackgroundColors.Primary.tertiary.current()
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
            colors = listOf(baseColor, highlightColor, baseColor),
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

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF7, name = "Shimmer · Placeholder Block Combo")
@Composable
private fun ShimmerPreview() {
    AppTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 头像圈 + 胶囊
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShimmerBlock(width = 56.dp, height = 56.dp, shape = CircleShape)
                ShimmerBlock(width = 150.dp, height = 52.dp, shape = RoundedCornerShape(26.dp))
            }
            // 文本行占位（自由组合 Modifier.shimmer 的用法）
            Box(
                Modifier
                    .size(240.dp, 20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .shimmer(),
            )
            Box(
                Modifier
                    .size(180.dp, 20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .shimmer(),
            )
        }
    }
}
