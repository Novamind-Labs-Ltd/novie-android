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
    /** Google Calendar 事件类型（见 [CalendarEventType]），仅作信息展示，不再区分会议/任务。 */
    val eventType: CalendarEventType = CalendarEventType.DEFAULT,
)
