package com.novamind.app.feature.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.theme.AppTheme

/** 统计卡片（Events / Tasks 计数）：可点击切换议程过滤，选中时带品牌色描边。 */
@Composable
internal fun StatCard(
    count: String,
    label: String,
    bg: Color,
    iconRes: Int,
    iconTint: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .then(if (selected) Modifier.border(2.dp, ColorPrimary, RoundedCornerShape(18.dp)) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .height(92.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconTint.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(48.dp),
        )
        Column(modifier = Modifier.padding(16.dp)) {
            Text(count, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
            Text(label, fontSize = 13.sp, color = ColorTextSub)
        }
    }
}

@Preview(showBackground = true, name = "Calendar · 统计卡片")
@Composable
private fun StatCardPreview() {
    AppTheme {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard("3", "Events", MeetingBg, R.drawable.ic_nav_calendar, MeetingIcon, Modifier.weight(1f))
            StatCard(
                "2", "Tasks", TodoBg, R.drawable.ic_check_circle, TodoIcon, Modifier.weight(1f),
                selected = true,
            )
        }
    }
}
