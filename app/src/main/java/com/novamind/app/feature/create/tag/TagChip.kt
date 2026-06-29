package com.novamind.app.feature.create.tag

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

@Composable
fun TagChip(
    tag: Tag,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 选中统一用品牌色（来自 ui/colors），不按各 tag 的 colorHex 取色
    val tagColor = IconColors.Brand.default.current()
    val bgColor = if (isSelected) tagColor.copy(alpha = 0.15f) else BackgroundColors.Surface.default.current()
    val borderColor = if (isSelected) tagColor else BorderColors.Default.default.current()

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20))   // 先裁圆角，使按压 ripple 也是圆角
            .border(1.dp, borderColor, RoundedCornerShape(20))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
        shape = RoundedCornerShape(20),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tag.name,
                fontSize = 13.sp,
                color = if (isSelected) tagColor else TextColors.Primary.secondary.current(),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0EFEA)
@Composable
private fun TagChipPreview() {
    AppTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TagChip(tag = Tag(id = "1", name = "Research"), isSelected = true, onClick = {})
            TagChip(tag = Tag(id = "2", name = "Design"), isSelected = false, onClick = {})
        }
    }
}
