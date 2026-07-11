package com.novamind.app.feature.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.theme.AppTheme

/** 周条单日格：星期字母 + 日号，选中日号带品牌色圆底，周末字母置灰。 */
@Composable
internal fun DayCell(
    letter: String,
    day: Int,
    selected: Boolean,
    weekend: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(letter, fontSize = 12.sp, color = if (weekend) ColorTextFaint else ColorTextSub)
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .then(if (selected) Modifier.background(ColorPrimary) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.toString(),
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
            DayCell(letter = "M", day = 1, selected = false, weekend = false, modifier = Modifier.weight(1f)) {}
            DayCell(letter = "T", day = 2, selected = true, weekend = false, modifier = Modifier.weight(1f)) {}
            DayCell(letter = "S", day = 6, selected = false, weekend = true, modifier = Modifier.weight(1f)) {}
        }
    }
}
