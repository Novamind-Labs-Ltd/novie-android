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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.data.calendar.CalendarEvent
import com.novamind.app.data.calendar.CalendarEventType
import com.novamind.app.data.calendar.isPast
import com.novamind.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

/** 活动行：圆点 + 标题/地点 + 开始时间；已结束的活动置灰 + 删除线（视为「已完成」）。 */
@Composable
internal fun EventRow(event: CalendarEvent) {
    val past = event.isPast
    val accent = if (past) ColorTextFaint else MeetingIcon
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ColorSurface)
            .border(1.dp, ColorBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        Column(Modifier.weight(1f)) {
            Text(
                text = event.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (past) ColorTextFaint else ColorTextTitle,
                textDecoration = if (past) TextDecoration.LineThrough else null,
            )
            event.location?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 12.sp, color = ColorTextSub)
            }
        }
        Text(
            text = if (event.isAllDay) "All day" else event.start.format(timeFormatter),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = ColorTextSub,
        )
    }
}

@Preview(showBackground = true, name = "Calendar · Event row")
@Composable
private fun EventRowPreview() {
    val day = LocalDate.now()
    AppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EventRow(
                CalendarEvent(
                    "1", "Team standup", false, day.atTime(9, 30), day.atTime(10, 0),
                    "Meet", CalendarEventType.DEFAULT, isMeeting = true,
                ),
            )
            // 已结束事件：置灰 + 删除线
            EventRow(
                CalendarEvent(
                    "0", "Morning sync", false,
                    day.minusDays(1).atTime(9, 30), day.minusDays(1).atTime(10, 0),
                    "Meet", CalendarEventType.DEFAULT,
                ),
            )
        }
    }
}
