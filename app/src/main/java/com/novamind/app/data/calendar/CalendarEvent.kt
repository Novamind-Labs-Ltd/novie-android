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
    /** 事件描述（Google Calendar description）；可能含 HTML/富文本，UI 层按需清洗。放末尾避免影响位置参数调用。 */
    val description: String? = null,
    /** 参会者列表（Google Calendar attendees）；供会议详情/列表展示，含本人（self=true）。默认空表无参会者。 */
    val attendees: List<CalendarAttendee> = emptyList(),
)

/** 会议参会者（Google Calendar attendee 的领域投影，与 API DTO 解耦）。 */
data class CalendarAttendee(
    /** 邮箱；资源型参会者（会议室等）可能为 null。 */
    val email: String?,
    /** 显示名；缺省时 UI 可回退到邮箱。 */
    val displayName: String?,
    /** 是否为当前用户本人。 */
    val self: Boolean,
    /** 应答状态。 */
    val responseStatus: AttendeeResponse,
)

/** 参会者应答状态（Google Calendar responseStatus）。 */
enum class AttendeeResponse {
    /** 已接受。 */
    ACCEPTED,

    /** 已拒绝。 */
    DECLINED,

    /** 待定（可能出席）。 */
    TENTATIVE,

    /** 尚未响应。 */
    NEEDS_ACTION,

    /** 未知 / 未识别（前向兼容）。 */
    UNKNOWN;

    companion object {
        fun fromApi(raw: String?): AttendeeResponse = when (raw) {
            "accepted" -> ACCEPTED
            "declined" -> DECLINED
            "tentative" -> TENTATIVE
            "needsAction" -> NEEDS_ACTION
            else -> UNKNOWN
        }
    }
}

/**
 * 事件是否已结束（视为「已完成」）。日历事件本身没有完成状态，此处以结束时间是否早于当前时间近似。
 * 全天事件的 [CalendarEvent.start] / [CalendarEvent.end] 仅为 00:00 占位，故按日期判断：当天结束后才算过去。
 */
val CalendarEvent.isPast: Boolean
    get() = if (isAllDay) end.toLocalDate().isBefore(LocalDate.now())
            else end.isBefore(LocalDateTime.now())
