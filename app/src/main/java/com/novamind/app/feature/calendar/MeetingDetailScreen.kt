package com.novamind.app.feature.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.data.calendar.CalendarAttendee
import com.novamind.app.data.calendar.CalendarEvent
import com.novamind.app.data.calendar.CalendarEventType
import com.novamind.app.data.calendar.leadLabel
import com.novamind.app.feature.calendar.components.BgPage
import com.novamind.app.feature.calendar.components.ColorBorder
import com.novamind.app.feature.calendar.components.ColorSurface
import com.novamind.app.feature.calendar.components.ColorTextSub
import com.novamind.app.feature.calendar.components.ColorTextTitle
import com.novamind.app.ui.components.TopBarBackButton
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
 * 会议详情页（Figma My-Novie 959-62071 / 1032-42662）：顶部返回 + 「Meetings」标题；下方白色卡片依次
 * 展示事件名、时间、地点、提醒、邀请人、描述。各字段随 [CalendarEvent] 有值时才渲染（附件暂未接入）。
 *
 * 无状态：仅消费 [event] 与 [onBack]，作为全屏覆盖层由 [CalendarRoute] 编排。
 */
@Composable
fun MeetingDetailScreen(
    event: CalendarEvent,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 点击顶部铅笔进入编辑；为 null 时不显示编辑入口。 */
    onEdit: (() -> Unit)? = null,
) {
    BackHandler(onBack = onBack)

    val timeText = if (event.isAllDay) {
        "${event.start.format(detailDateFormatter)} · All day"
    } else {
        "${event.start.format(detailDateFormatter)} · " +
            "${event.start.format(detailTimeFormatter)} → ${event.end.format(detailTimeFormatter)}"
    }
    val location = event.location?.takeIf { it.isNotBlank() }
    val meetingUrl = event.meetingUrl?.takeIf { it.isNotBlank() }
    val description = event.description?.stripHtml()?.takeIf { it.isNotBlank() }
    val uriHandler = LocalUriHandler.current
    // 提醒（Figma 铃铛行「1 hour」）：多条用逗号连接，如「1 hour, 10 minutes」；无提醒则不显示该行。
    val reminderText = event.reminders.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.leadLabel() }
    // 参会人员摘要（Figma「Jerry & 50 others」）：优先取非本人的参会者做代表名，其余计入 "& N others"。
    val attendees = event.attendees
    val inviteesText = when {
        attendees.isEmpty() -> null
        attendees.size == 1 -> attendees.first().label()
        else -> {
            val primary = attendees.firstOrNull { !it.self } ?: attendees.first()
            "${primary.label()} & ${attendees.size - 1} others"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // 顶部信息（Figma 1365-42670）：返回按钮与 22sp 标题同排。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 22.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopBarBackButton(onClick = onBack)
            Text(
                text = "Meetings",
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                color = ColorTextTitle,
                modifier = Modifier.padding(start = 12.dp),
            )
            if (onEdit != null) {
                Spacer(Modifier.weight(1f))
                Surface(shape = CircleShape, color = ColorSurface, shadowElevation = 1.dp) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onEdit),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pencil_line),
                            contentDescription = "Edit meeting",
                            tint = ColorTextTitle,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // 事件卡片（Figma Body - Event）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ColorSurface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = event.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = ColorTextTitle,
                lineHeight = 30.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )

            DetailRow(iconRes = R.drawable.ic_clock, primary = timeText)

            if (meetingUrl != null || location != null || reminderText != null ||
                inviteesText != null || description != null
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ColorBorder),
                )
            }

            // 会议链接（Google Meet 等）：可点击整行，用系统浏览器/对应 App 打开视频通话入口。
            meetingUrl?.let { url ->
                DetailRow(
                    iconRes = R.drawable.ic_link,
                    primary = "Join meeting",
                    secondary = url.meetingLinkLabel(),
                    onClick = { uriHandler.openUri(url) },
                )
            }
            location?.let { DetailRow(iconRes = R.drawable.ic_location, primary = it) }
            reminderText?.let { DetailRow(iconRes = R.drawable.ic_notification, primary = it) }
            inviteesText?.let { DetailRow(iconRes = R.drawable.ic_meeting_people, primary = it) }
            description?.let {
                DetailRow(iconRes = R.drawable.ic_format_list, primary = it, multiline = true)
            }
        }
    }
}

/** 参会者展示名：昵称优先，其次邮箱，最后兜底 "Guest"（资源型参会者可能都为空）。 */
private fun CalendarAttendee.label(): String =
    displayName?.takeIf { it.isNotBlank() } ?: email?.takeIf { it.isNotBlank() } ?: "Guest"

/** 会议链接展示文案：去掉 scheme 与末尾斜杠，如 https://meet.google.com/abc/ → meet.google.com/abc。 */
private fun String.meetingLinkLabel(): String =
    substringAfter("://").trimEnd('/')

/** 详情行：圆形留白内的小图标 + 主文案（可选副文案），与 Figma 各信息行一致。整行可选点击（[onClick]）。 */
@Composable
private fun DetailRow(
    iconRes: Int,
    primary: String,
    secondary: String? = null,
    multiline: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
                meetingUrl = "https://meet.google.com/abc-defg-hij",
                description = "Time to coordinate team workflows, align on priorities, " +
                    "and uncover project roadblocks.",
                attendees = listOf(
                    CalendarAttendee("me@novamind.ai", "Me", self = true, responseStatus = com.novamind.app.data.calendar.AttendeeResponse.ACCEPTED),
                    CalendarAttendee("jerry@novamind.ai", "Jerry", self = false, responseStatus = com.novamind.app.data.calendar.AttendeeResponse.ACCEPTED),
                    CalendarAttendee("alice@novamind.ai", "Alice", self = false, responseStatus = com.novamind.app.data.calendar.AttendeeResponse.TENTATIVE),
                ),
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
