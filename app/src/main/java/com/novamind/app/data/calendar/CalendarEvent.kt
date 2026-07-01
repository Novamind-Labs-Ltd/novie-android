package com.novamind.app.data.calendar

import java.time.LocalDate
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
    /**
     * 是否为「会议」：eventType 为 [CalendarEventType.DEFAULT] 且（有除自己外的邀请人 或 有会议链接）。
     * 判定在数据层映射时完成（依赖 API 的 attendees / hangoutLink / conferenceData，领域层不保留原始字段）。
     */
    val isMeeting: Boolean = false,
)

/**
 * 事件是否已结束（视为「已完成」）。日历事件本身没有完成状态，此处以结束时间是否早于当前时间近似。
 * 全天事件的 [CalendarEvent.start] / [CalendarEvent.end] 仅为 00:00 占位，故按日期判断：当天结束后才算过去。
 */
val CalendarEvent.isPast: Boolean
    get() = if (isAllDay) end.toLocalDate().isBefore(LocalDate.now())
            else end.isBefore(LocalDateTime.now())
