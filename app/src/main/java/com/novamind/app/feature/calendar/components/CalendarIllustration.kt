package com.novamind.app.feature.calendar.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.novamind.app.ui.colors.Palette
import com.novamind.app.ui.theme.AppTheme

/** 空状态插图：叠放的笔记本/文件夹 + 装饰圆点（纯 Canvas，无图片资源）。 */
@Composable
internal fun CalendarIllustration() {
    // Canvas DrawScope 非 @Composable，颜色令牌须在此先解析为 Color 再传入。
    val primary = ColorPrimary
    val accent = MeetingIcon
    val shadow = Palette.black0
    val cover = Palette.sand550
    val page = Palette.white
    val dotLarge = Palette.sand700
    val dotSmall = Palette.sand550
    Canvas(modifier = Modifier.size(width = 168.dp, height = 124.dp)) {
        val w = size.width
        val h = size.height
        drawOval(shadow, topLeft = Offset(w * 0.20f, h * 0.84f), size = Size(w * 0.60f, h * 0.12f))
        // 后封面
        drawRoundRect(cover, topLeft = Offset(w * 0.26f, h * 0.18f), size = Size(w * 0.46f, h * 0.56f), cornerRadius = CornerRadius(10f, 10f))
        // 白页
        drawRoundRect(page, topLeft = Offset(w * 0.31f, h * 0.24f), size = Size(w * 0.40f, h * 0.52f), cornerRadius = CornerRadius(8f, 8f))
        // 绿色书签/卡
        drawRoundRect(primary, topLeft = Offset(w * 0.30f, h * 0.46f), size = Size(w * 0.14f, h * 0.14f), cornerRadius = CornerRadius(4f, 4f))
        drawRoundRect(accent, topLeft = Offset(w * 0.50f, h * 0.58f), size = Size(w * 0.12f, h * 0.12f), cornerRadius = CornerRadius(4f, 4f))
        // 装饰
        drawCircle(dotLarge, radius = w * 0.05f, center = Offset(w * 0.80f, h * 0.30f))
        drawCircle(dotSmall, radius = w * 0.055f, center = Offset(w * 0.18f, h * 0.66f))
        drawCircle(primary.copy(alpha = 0.4f), radius = w * 0.016f, center = Offset(w * 0.74f, h * 0.7f))
    }
}

@Preview(showBackground = true, name = "Calendar · 空状态插图")
@Composable
private fun CalendarIllustrationPreview() {
    AppTheme { CalendarIllustration() }
}
