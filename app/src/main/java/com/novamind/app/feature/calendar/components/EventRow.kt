package com.novamind.app.feature.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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
 * 会议行（Figma 1032-42476）：圆角卡片 + 左侧**深色圆角方块**图标框（白色双人图标）+ 标题 + 时间段。
 * 已结束（[CalendarEvent.isPast]）为「已过去」态：卡片改浅底（#fcfaf6）、图标框改灰底（#e5e5e5）配
 * 次要色图标、标题加删除线并转次要色。
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
    // 卡片：未过去=白底(surface)；已过去=浅底(#fcfaf6)。均带 card 阴影、圆角 12。
    val cardBg = if (past) ColorCardBg else ColorSurface
    // 图标框：未过去=深色(#242424)白图标；已过去=灰底(#e5e5e5)次要色图标。圆角 8、四周 12 内距。
    val boxBg = if (past) TodoBg else ColorDark
    val iconTint = if (past) ColorTextSub else ColorOnPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(boxBg)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_meeting_people),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(width = 21.5.dp, height = 16.5.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = event.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (past) ColorTextSub else ColorTextTitle,
                textDecoration = if (past) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (timeRange.isNotBlank()) {
                Text(
                    text = timeRange,
                    fontSize = 12.sp,
                    color = ColorTextSub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EventRow(
                CalendarEvent(
                    "1", "Coffee chat", false, day.atTime(12, 0), day.atTime(13, 0),
                    null, CalendarEventType.DEFAULT, isMeeting = true,
                ),
            )
            // 已过去（昨天）：浅底卡片 + 灰图标框 + 删除线标题
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
