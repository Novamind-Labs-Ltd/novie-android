package com.novamind.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
 * 空数据空状态页：居中的「空盒子」图示 + 阴影 + 文案 +（可选）操作按钮。
 *
 * 通用可复用组件，列表/内容为空时铺满展示。文案与操作均可配置：
 * [onAction] 为 null 时不显示按钮（纯提示）。配色全部取设计系统令牌（[com.novamind.app.ui.colors]），
 * 随深浅主题自动解析；图示为纯 Canvas 绘制，不依赖任何 drawable 资源。
 *
 * ```
 * EmptyView(message = "暂无笔记", actionText = "新建", onAction = { create() })
 * ```
 */
@Composable
fun EmptyView(
    modifier: Modifier = Modifier,
    message: String = "暂无数据",
    actionText: String = "",
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundColors.Page.default.current())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyBoxIllustration(
            tint = TextColors.Primary.tertiary.current(),
            size = 108.dp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            fontSize = 15.sp,
            color = TextColors.Primary.secondary.current(),
            textAlign = TextAlign.Center,
        )
        if (onAction != null && actionText.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = actionText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextColors.Inverse.default.current(),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(BackgroundColors.Primary.default.current())
                    .clickable(onClick = onAction)
                    .padding(horizontal = 28.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * 「空盒子」图示：一个开口的托盘/纸箱（含中间开口凹槽）+ 底部柔和阴影椭圆。纯 Canvas 绘制。
 * 画布宽高比约 4:3；[size] 为宽度。
 */
@Composable
private fun EmptyBoxIllustration(
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(width = size, height = size * 0.78f)) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = minOf(w, h) * 0.035f
        val stroke = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // 底部阴影椭圆（比盒子更淡）
        drawOval(
            color = tint.copy(alpha = 0.18f),
            topLeft = Offset(w * 0.22f, h * 0.88f),
            size = Size(w * 0.56f, h * 0.10f),
        )

        // 盒身（前面板 + 开口凹槽）：左壁 → 顶沿（中间下凹形成开口）→ 右壁 → 底 → 闭合
        val body = Path().apply {
            moveTo(w * 0.16f, h * 0.50f)   // 左沿
            lineTo(w * 0.34f, h * 0.50f)
            lineTo(w * 0.41f, h * 0.60f)   // 下凹（开口）
            lineTo(w * 0.59f, h * 0.60f)
            lineTo(w * 0.66f, h * 0.50f)
            lineTo(w * 0.84f, h * 0.50f)   // 右沿
            lineTo(w * 0.78f, h * 0.82f)   // 右壁到底
            lineTo(w * 0.22f, h * 0.82f)   // 底
            close()                         // 左壁回到左沿
        }
        drawPath(body, color = tint, style = stroke)

        // 折向后方的盖片：两侧斜边 + 后顶边
        drawLine(tint, Offset(w * 0.16f, h * 0.50f), Offset(w * 0.30f, h * 0.24f), strokeW, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.84f, h * 0.50f), Offset(w * 0.70f, h * 0.24f), strokeW, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.30f, h * 0.24f), Offset(w * 0.70f, h * 0.24f), strokeW, cap = StrokeCap.Round)
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Empty · default")
@Composable
private fun EmptyDefaultPreview() {
    AppTheme {
        EmptyView()
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Empty · with action")
@Composable
private fun EmptyWithActionPreview() {
    AppTheme {
        EmptyView(message = "暂无笔记", actionText = "新建", onAction = {})
    }
}
