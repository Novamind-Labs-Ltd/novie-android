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
    /** Google Calendar 事件类型（见 [CalendarEventType]）。缺省为 [CalendarEventType.DEFAULT]。 */
    val eventType: CalendarEventType = CalendarEventType.DEFAULT,
) {
    /**
     * 基于 [eventType] 的分类：
     * default → [CalendarEventCategory.MEETING]，focusTime → [CalendarEventCategory.TASK]，
     * 其余（outOfOffice / workingLocation / birthday / fromGmail / unknown）→ [CalendarEventCategory.OTHER]。
     */
    val category: CalendarEventCategory
        get() = when (eventType) {
            CalendarEventType.DEFAULT -> CalendarEventCategory.MEETING
            CalendarEventType.FOCUS_TIME -> CalendarEventCategory.TASK
            else -> CalendarEventCategory.OTHER
        }

    /** 是否为「会议」类型（eventType==default）。 */
    val isMeetingType: Boolean get() = category == CalendarEventCategory.MEETING

    /** 是否为「任务」类型（eventType==focusTime）。 */
    val isTaskType: Boolean get() = category == CalendarEventCategory.TASK
}

/** 基于 [CalendarEventType] 的活动分类。 */
enum class CalendarEventCategory {
    /** 会议（eventType==default）。 */
    MEETING,

    /** 任务（eventType==focusTime）。 */
    TASK,

    /** 其他活动（outOfOffice / workingLocation / birthday / fromGmail / unknown）。 */
    OTHER,
}
