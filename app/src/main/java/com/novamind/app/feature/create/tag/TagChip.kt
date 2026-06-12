package com.novamind.app.feature.create.tag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SelectedGreen = Color(0xFF3D7A5A)

@Composable
fun TagChip(
    tag: Tag,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 选中统一用绿色，不按各 tag 的 colorHex 取色
    val tagColor = SelectedGreen
    val bgColor = if (isSelected) tagColor.copy(alpha = 0.15f) else Color.White
    val borderColor = if (isSelected) tagColor else Color(0xFFE0E0E0)

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))   // 先裁圆角，使按压 ripple 也是圆角
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
        shape = RoundedCornerShape(50),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tag.name,
                fontSize = 13.sp,
                color = if (isSelected) tagColor else Color(0xFF6B6B6B),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFF3D7A5A)
    }
}
