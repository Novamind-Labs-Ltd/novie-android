package com.novamind.app.feature.library.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.Palette
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

@Composable
internal fun EmptyState(onCreateNote: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyIllustration()
        Spacer(Modifier.height(28.dp))
        Text(
            text = "No notes yet.",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = ColorTextTitle,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Start a new note to organise your projects, tasks, or brainstorming sessions.",
            fontSize = 14.sp,
            color = ColorTextSub,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        // Create new note 按钮（黑色胶囊）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(BackgroundColors.Primary.default.current())
                .clickable(onClick = onCreateNote)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_create),
                    contentDescription = null,
                    tint = TextColors.Inverse.default.current(),
                    modifier = Modifier.size(18.dp),
                )
                Text("Create new note", color = TextColors.Inverse.default.current(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 空状态插图：叠放的笔记本/卡片 + 装饰圆点（纯 Canvas 绘制，无需图片资源）。 */
@Composable
private fun EmptyIllustration() {
    // 在 composable 作用域内解析设计系统颜色，供下方 Canvas（非 composable 作用域）使用
    val border = ColorBorder
    val accent = ColorAccent
    val paper = BgCard
    val shadow = Palette.black0
    val backBook = Palette.sand550
    val dot = Palette.sand600
    Canvas(modifier = Modifier.size(width = 176.dp, height = 140.dp)) {
        val w = size.width
        val h = size.height
        val center = Offset(w * 0.5f, h * 0.5f)

        // 底部柔和阴影
        drawOval(
            color = shadow,
            topLeft = Offset(w * 0.18f, h * 0.82f),
            size = Size(w * 0.64f, h * 0.12f),
        )
        // 后面一本（向左倾斜）
        rotate(degrees = -10f, pivot = center) {
            drawRoundRect(
                color = backBook,
                topLeft = Offset(w * 0.24f, h * 0.20f),
                size = Size(w * 0.46f, h * 0.52f),
                cornerRadius = CornerRadius(10f, 10f),
            )
        }
        // 中间白本（轻微右倾）
        rotate(degrees = 5f, pivot = center) {
            drawRoundRect(
                color = paper,
                topLeft = Offset(w * 0.28f, h * 0.24f),
                size = Size(w * 0.44f, h * 0.52f),
                cornerRadius = CornerRadius(10f, 10f),
            )
            drawRoundRect(
                color = border,
                topLeft = Offset(w * 0.28f, h * 0.24f),
                size = Size(w * 0.44f, h * 0.52f),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = 2f),
            )
        }
        // 前面：绿色书脊 + 白色页
        drawRoundRect(
            color = accent,
            topLeft = Offset(w * 0.30f, h * 0.40f),
            size = Size(w * 0.09f, h * 0.40f),
            cornerRadius = CornerRadius(6f, 6f),
        )
        drawRoundRect(
            color = paper,
            topLeft = Offset(w * 0.39f, h * 0.40f),
            size = Size(w * 0.30f, h * 0.40f),
            cornerRadius = CornerRadius(6f, 6f),
        )
        drawRoundRect(
            color = border,
            topLeft = Offset(w * 0.39f, h * 0.40f),
            size = Size(w * 0.30f, h * 0.40f),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = 2f),
        )
        // 装饰圆点
        drawCircle(color = dot, radius = w * 0.045f, center = Offset(w * 0.80f, h * 0.34f))
        drawCircle(color = accent.copy(alpha = 0.35f), radius = w * 0.018f, center = Offset(w * 0.20f, h * 0.30f))
        drawCircle(color = dot, radius = w * 0.014f, center = Offset(w * 0.78f, h * 0.66f))
    }
}

@Preview(showBackground = true, name = "Library · EmptyState")
@Composable
private fun EmptyStatePreview() {
    AppTheme {
        EmptyState(onCreateNote = {})
    }
}

@Preview(showBackground = true, name = "Library · EmptyIllustration")
@Composable
private fun EmptyIllustrationPreview() {
    AppTheme {
        EmptyIllustration()
    }
}
