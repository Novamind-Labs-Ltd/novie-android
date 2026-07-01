package com.novamind.app.data.tasks

import java.time.LocalDate

/**
 * 领域层「任务」（Google Tasks，与日历活动区分）。
 * Google Tasks 的 due 只精确到**日期**（无具体时间），故 [due] 用 [LocalDate]。
 */
data class CalendarTask(
    val id: String,
    val title: String,
    /** 截止日期（date-only）。无截止日的任务不归属到某一天。 */
    val due: LocalDate?,
    val isCompleted: Boolean,
    val notes: String?,
)
