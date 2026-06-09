package com.novamind.app.feature.create.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R

/**
 * 输入时显示的格式工具栏：可横向滑动的工具列表 + 固定的收起键盘按钮。
 */
@Composable
fun FormattingToolbar(
    onHideKeyboard: () -> Unit = {},
    onBold: () -> Unit = {},
    isBoldActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(50),
            color = ColorChipBg,
            shadowElevation = 2.dp,
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item { ToolbarIcon(R.drawable.ic_mic, "Voice") }
                item { ToolbarIcon(R.drawable.ic_attach, "Attach") }
                item { ToolbarIcon(R.drawable.ic_magic, "Magic") }
                item { ToolbarTextBtn("B", FontWeight.ExtraBold, active = isBoldActive, onClick = onBold) }
                item { ToolbarTextBtn("I", FontWeight.Bold, fontStyle = FontStyle.Italic) }
                item { ToolbarIcon(R.drawable.ic_format_list, "List") }
            }
        }
        Surface(shape = CircleShape, color = ColorChipBg, shadowElevation = 2.dp) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false),
                        onClick = onHideKeyboard,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard_hide),
                    contentDescription = "Hide keyboard",
                    tint = ColorTextTitle,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolbarIcon(iconResId: Int, contentDescription: String) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = contentDescription,
            tint = ColorTextTitle,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ToolbarTextBtn(
    text: String,
    fontWeight: FontWeight,
    fontStyle: FontStyle = FontStyle.Normal,
    active: Boolean = false,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) ColorPrimary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 16.sp,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            color = if (active) ColorPrimary else ColorTextTitle,
        )
    }
}
