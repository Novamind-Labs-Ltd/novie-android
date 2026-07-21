package com.novamind.app.feature.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.theme.AppTheme

/**
 * 周条单日格（Figma 959-60214）：仅日号（星期字母为卡片内独立表头行，不在此）。
 * 选中日号为深色圆底 + 白字（[ColorDark]），未选中为普通文字。
 */
@Composable
internal fun DayCell(
    day: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(if (selected) Modifier.background(ColorDark) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.toString(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = if (selected) ColorTextInverse else ColorTextTitle,
            )
        }
    }
}

@Preview(showBackground = true, name = "Calendar · Week bar day cell")
@Composable
private fun DayCellPreview() {
    AppTheme {
        Row {
            DayCell(day = 7, selected = false, modifier = Modifier.weight(1f)) {}
            DayCell(day = 10, selected = true, modifier = Modifier.weight(1f)) {}
            DayCell(day = 13, selected = false, modifier = Modifier.weight(1f)) {}
        }
    }
}
