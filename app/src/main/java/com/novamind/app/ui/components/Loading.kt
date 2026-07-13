package com.novamind.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

/**
 * 基础加载指示器：品牌色的转圈。用于按钮、卡片、局部区域等就地展示加载态。
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    strokeWidth: Dp = 3.dp,
    color: Color = IconColors.Brand.default.current(),
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        strokeWidth = strokeWidth,
    )
}

/**
 * 全局加载遮罩：铺满父容器，半透明遮罩 + 居中的转圈 HUD，[visible] 为 true 时显示。
 *
 * 会**拦截触摸**，阻止加载期间点到下层内容。用法：放在页面根 [Box] 的最后一层，例如
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     Content(...)
 *     LoadingOverlay(visible = uiState.isLoading)
 * }
 * ```
 */
@Composable
fun LoadingOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    /** 遮罩透明度（0=全透明，1=不透明）；默认 0.15 的黑色，半透明可透出下层。 */
    scrimAlpha: Float = 0.15f,
    scrim: Color = Color.Black.copy(alpha = scrimAlpha),
) {
    if (!visible) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scrim)
            // 吞掉点击，拦截下层交互（无涟漪）
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BackgroundColors.Interactive.default.current(),
            shadowElevation = 6.dp,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)),
        ) {
            LoadingIndicator(modifier = Modifier.padding(20.dp))
        }
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Loading · Indicator")
@Composable
private fun LoadingIndicatorPreview() {
    AppTheme {
        LoadingIndicator(modifier = Modifier.padding(24.dp))
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Loading · Overlay")
@Composable
private fun LoadingOverlayPreview() {
    AppTheme {
        Box(Modifier.fillMaxSize()) {
            LoadingOverlay(visible = true)
        }
    }
}
