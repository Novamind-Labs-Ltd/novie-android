package com.novamind.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

/**
 * 无网络空状态页：居中的「无 WiFi」图示 + 标题 + 说明 +（可选）重试按钮。
 *
 * 通用可复用组件，供各页面在网络不可用时铺满展示。文案与重试均可配置：
 * [onRetry] 为 null 时不显示重试按钮（如仅提示、由外部下拉刷新重试的场景）。
 * 配色全部取设计系统令牌（[com.novamind.app.ui.colors]），随深浅主题自动解析。
 *
 * ```
 * NoNetworkView(onRetry = { viewModel.reload() })
 * ```
 */
@Composable
fun NoNetworkView(
    modifier: Modifier = Modifier,
    title: String = "No internet connection",
    message: String = "Please check your connection and try again.",
    retryText: String = "Retry",
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundColors.Page.default.current())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WifiOffIllustration(
            tint = TextColors.Primary.tertiary.current(),
            size = 96.dp,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextColors.Primary.default.current(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = TextColors.Primary.secondary.current(),
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = retryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextColors.Inverse.default.current(),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(BackgroundColors.Primary.default.current())
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 28.dp, vertical = 12.dp),
            )
        }
    }
}

/** 「无 WiFi」图示：三段同心弧 + 底部圆点 + 斜杠（表示断开）。纯 Canvas 绘制，不依赖资源。 */
@Composable
private fun WifiOffIllustration(
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val side = this.size.minDimension
        val cx = this.size.width / 2f
        val baseY = this.size.height * 0.78f      // 底部发射点
        val stroke = side * 0.07f

        // 底部圆点
        drawCircle(color = tint, radius = stroke * 0.9f, center = Offset(cx, baseY))
        // 三段向上张开的同心弧
        listOf(0.26f, 0.46f, 0.66f).forEach { f ->
            val r = side * f
            drawArc(
                color = tint,
                startAngle = 205f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(cx - r, baseY - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        // 斜杠：左上 → 右下，表示网络断开
        drawLine(
            color = tint,
            start = Offset(this.size.width * 0.18f, this.size.height * 0.16f),
            end = Offset(this.size.width * 0.82f, this.size.height * 0.80f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "NoNetwork · with retry")
@Composable
private fun NoNetworkWithRetryPreview() {
    AppTheme {
        NoNetworkView(onRetry = {})
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "NoNetwork · no retry")
@Composable
private fun NoNetworkNoRetryPreview() {
    AppTheme {
        NoNetworkView()
    }
}
