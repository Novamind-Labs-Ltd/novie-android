package com.novamind.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.novamind.app.R
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.current

private val SpinnerColor: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.default.current()   // #333

/**
 * 下拉刷新的加载图标（Figma: status-loading，辐条 spinner）：
 * - 下拉中（[isRefreshing]=false）：图标随 [fraction] 旋转、淡入呼应手势；
 * - 刷新中：持续匀速旋转。
 *
 * 纯展示组件，位置/淡入由 [AppPullToRefresh] 控制。图标为 ic_status_loading（占位矢量，
 * 待真素材可下载后替换）；tint 会保留辐条的透明度拖尾。
 */
@Composable
fun AppPullRefreshIndicator(
    isRefreshing: Boolean,
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = SpinnerColor,
) {
    val f = fraction.coerceIn(0f, 1f)
    if (!isRefreshing && f <= 0f) return

    val rotation = if (isRefreshing) {
        val transition = rememberInfiniteTransition(label = "loading_spin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "loading_angle",
        )
        angle
    } else {
        f * 360f
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(id = R.drawable.ic_status_loading),
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(24.dp)
                .rotate(rotation),
        )
    }
}
