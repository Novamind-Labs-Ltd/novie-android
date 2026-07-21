package com.novamind.app.feature.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 议程行统一样式（Figma 959-61626）：
 * - 未完成：白色卡片 + 柔和阴影 + 彩色圆形图标（会议 green-100 人物 / 待办 slate 清单）。
 * - 已完成：无卡片底、圆形改为灰色描边 + done-all 双勾图标；标题可选置灰 + 删除线
 *   （待办删除线，会议保持深色不加删除线）。
 */
@Composable
internal fun AgendaRow(
    done: Boolean,
    iconPainter: Painter,
    iconTint: Color,
    circleBg: Color,
    title: String,
    subtitle: String?,
    strikeThroughWhenDone: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val struck = done && strikeThroughWhenDone
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (done) {
                    Modifier
                } else {
                    Modifier
                        .shadow(3.dp, RoundedCornerShape(12.dp), clip = false)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ColorSurface)
                },
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .then(
                    if (done) Modifier.border(1.dp, ColorBorderStrong, CircleShape)
                    else Modifier.background(circleBg),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (struck) ColorTextSub else ColorTextTitle,
                textDecoration = if (struck) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 12.sp, color = ColorTextSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
