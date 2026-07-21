package com.novamind.app.feature.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.data.calendar.CalendarEvent
import com.novamind.app.data.calendar.CalendarEventType
import com.novamind.app.feature.calendar.components.BgPage
import com.novamind.app.feature.calendar.components.ColorBorder
import com.novamind.app.feature.calendar.components.ColorSurface
import com.novamind.app.feature.calendar.components.ColorTextSub
import com.novamind.app.feature.calendar.components.ColorTextTitle
import com.novamind.app.ui.components.BackButton
import com.novamind.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val detailDateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val detailTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** 去除 Google Calendar description 里的 HTML 标签与实体，得到纯文本（模型注释要求 UI 层按需清洗）。 */
private fun String.stripHtml(): String =
    replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

/**
 * 会议详情页（Figma My-Novie 959-62071）：顶部返回 + 「Meetings」标题；下方白色卡片依次展示
 * 事件名、时间、地点、描述。字段随 [CalendarEvent] 有值时才渲染（领域模型暂无提醒/邀请人/附件）。
 *
 * 无状态：仅消费 [event] 与 [onBack]，作为全屏覆盖层由 [CalendarRoute] 编排。
 */
@Composable
fun MeetingDetailScreen(
    event: CalendarEvent,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val timeText = if (event.isAllDay) {
        "${event.start.format(detailDateFormatter)} · All day"
    } else {
        "${event.start.format(detailDateFormatter)} · " +
            "${event.start.format(detailTimeFormatter)} → ${event.end.format(detailTimeFormatter)}"
    }
    val location = event.location?.takeIf { it.isNotBlank() }
    val description = event.description?.stripHtml()?.takeIf { it.isNotBlank() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // 顶部信息（Figma top_info）：返回按钮 + 大标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack)
        }
        Text(
            text = "Meetings",
            fontSize = 32.sp,
            fontWeight = FontWeight.Medium,
            color = ColorTextTitle,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
        )

        // 事件卡片（Figma Body - Event）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ColorSurface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = event.title,
                fontSize = 16.sp,
                color = ColorTextSub,
                lineHeight = 24.sp,
            )

            DetailRow(iconRes = R.drawable.ic_clock, primary = timeText)

            if (location != null || description != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ColorBorder),
                )
            }

            location?.let { DetailRow(iconRes = R.drawable.ic_location, primary = it) }
            description?.let {
                DetailRow(iconRes = R.drawable.ic_format_list, primary = it, multiline = true)
            }
        }
    }
}

/** 详情行：圆形留白内的小图标 + 主文案（可选副文案），与 Figma 各信息行一致。 */
@Composable
private fun DetailRow(
    iconRes: Int,
    primary: String,
    secondary: String? = null,
    multiline: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = ColorTextTitle,
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = primary, fontSize = 14.sp, color = ColorTextTitle, lineHeight = 20.sp)
            secondary?.let {
                Text(text = it, fontSize = 12.sp, color = ColorTextSub, lineHeight = 16.sp)
            }
        }
    }
}

// ── Preview ──

@Preview(showBackground = true, showSystemUi = true, name = "Meeting detail")
@Composable
private fun MeetingDetailScreenPreview() {
    val day = LocalDate.now()
    AppTheme {
        MeetingDetailScreen(
            event = CalendarEvent(
                id = "1",
                title = "Team stand-up",
                isAllDay = false,
                start = day.atTime(10, 0),
                end = day.atTime(11, 0),
                location = "Novamind Labs, 34 Triton Drive, Rosedale, Auckland 0632",
                eventType = CalendarEventType.DEFAULT,
                isMeeting = true,
                description = "Time to coordinate team workflows, align on priorities, " +
                    "and uncover project roadblocks.",
            ),
            onBack = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Meeting detail · minimal")
@Composable
private fun MeetingDetailScreenMinimalPreview() {
    val day = LocalDate.now()
    AppTheme {
        MeetingDetailScreen(
            event = CalendarEvent(
                id = "2",
                title = "Focus time",
                isAllDay = true,
                start = day.atStartOfDay(),
                end = day.atStartOfDay(),
                location = null,
                eventType = CalendarEventType.FOCUS_TIME,
            ),
            onBack = {},
        )
    }
}
