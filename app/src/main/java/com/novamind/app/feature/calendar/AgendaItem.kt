package com.novamind.app.feature.calendar

import com.novamind.app.data.calendar.CalendarEvent
import com.novamind.app.data.tasks.CalendarTask
import java.time.LocalDateTime

/**
 * 议程条目：把「活动」和「任务」统一到一条按时间排序的时间线里。
 * [sortKey] 用于排序——活动按开始时间；任务无具体时间（date-only），排到当天最前（当天 00:00）。
 */
sealed interface AgendaItem {
    val sortKey: LocalDateTime

    data class Event(val event: CalendarEvent) : AgendaItem {
        override val sortKey: LocalDateTime get() = event.start
    }

    data class Task(val task: CalendarTask) : AgendaItem {
        override val sortKey: LocalDateTime
            get() = task.due?.atStartOfDay() ?: LocalDateTime.MIN
    }
}
