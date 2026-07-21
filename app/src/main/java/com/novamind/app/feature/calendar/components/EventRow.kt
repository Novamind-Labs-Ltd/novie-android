package com.novamind.app.feature.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.data.calendar.CalendarEvent
import com.novamind.app.data.calendar.CalendarEventType
import com.novamind.app.data.calendar.isPast
import com.novamind.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/**
 * 活动行（Figma 959-61773）：未结束为白色卡片 + 绿色圆形会议图标 + 标题 + 时间段；
 * 已结束（视为「已完成」）改为无卡片底、灰色描边圆形对勾图标、文字置灰 + 删除线。
 */
@Composable
internal fun EventRow(event: CalendarEvent) {
    val past = event.isPast
    val timeRange = if (event.isAllDay) {
        "All day"
    } else {
        "${event.start.format(timeFormatter)} - ${event.end.format(timeFormatter)}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (past) {
                    Modifier
                } else {
                    Modifier.clip(RoundedCornerShape(16.dp)).background(ColorSurface)
                },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧圆形图标：未结束=绿色淡底会议图标；已结束=灰色描边对勾
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(
                    if (past) Modifier.border(1.5.dp, ColorBorder, CircleShape)
                    else Modifier.background(MeetingBg),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (past) R.drawable.ic_check else R.drawable.ic_upcoming_meeting),
                contentDescription = null,
                tint = if (past) ColorTextFaint else MeetingIcon,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = event.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (past) ColorTextFaint else ColorTextTitle,
                textDecoration = if (past) TextDecoration.LineThrough else null,
            )
            Text(timeRange, fontSize = 13.sp, color = ColorTextSub)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Calendar · Event row")
@Composable
private fun EventRowPreview() {
    val day = LocalDate.now()
    AppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EventRow(
                CalendarEvent(
                    "1", "Coffee chat", false, day.atTime(12, 0), day.atTime(13, 0),
                    null, CalendarEventType.DEFAULT, isMeeting = true,
                ),
            )
            EventRow(
                CalendarEvent(
                    "0", "Focus time", false,
                    day.minusDays(1).atTime(15, 0), day.minusDays(1).atTime(16, 0),
                    null, CalendarEventType.DEFAULT,
                ),
            )
        }
    }
}
