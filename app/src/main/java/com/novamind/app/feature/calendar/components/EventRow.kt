package com.novamind.app.feature.calendar.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
 * 活动行（Figma 959-61626）：未结束为白色卡片 + green-100 圆形人物图标 + 标题 + 时间段；
 * 已结束（视为「已完成」）改为无卡片底、灰色描边圆形 + done-all 双勾图标（标题保持深色，不加删除线）。
 *
 * @param onClick 点击整行回调（进入会议详情，Figma 959-62071）；为 null 时不可点击。
 */
@Composable
internal fun EventRow(event: CalendarEvent, onClick: (() -> Unit)? = null) {
    val past = event.isPast
    val timeRange = if (event.isAllDay) {
        "All day"
    } else {
        "${event.start.format(timeFormatter)} - ${event.end.format(timeFormatter)}"
    }
    AgendaRow(
        done = past,
        iconPainter = painterResource(if (past) R.drawable.ic_done_all else R.drawable.ic_meeting_people),
        iconTint = if (past) ColorTextSub else ColorTextTitle,
        circleBg = MeetingBg,
        title = event.title,
        subtitle = timeRange,
        strikeThroughWhenDone = false,
        onClick = onClick,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Calendar · Event row")
@Composable
private fun EventRowPreview() {
    val day = LocalDate.now()
    AppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
