package com.novamind.app.data.calendar

import java.time.LocalDateTime

/**
 * 领域层日历事件（与 Google API 解耦的纯 Kotlin 模型）。
 * 时间已转换为目标时区的本地时间，UI 层无需再处理时区。
 */
data class CalendarEvent(
    val id: String,
    val title: String,
    /** 全天事件为 true，此时 [start] / [end] 为当天 00:00 仅作占位。 */
    val isAllDay: Boolean,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val location: String?,
    /** 是否为「会议」：含本人之外的参与者，或带视频会议链接。否则视为个人待办/日程。 */
    val isMeeting: Boolean,
) {
    /** 上午（开始时间早于 12:00）；全天事件归入上午。 */
    val isMorning: Boolean get() = isAllDay || start.hour < 12
}
