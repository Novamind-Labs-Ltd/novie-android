package com.novamind.app.data.calendar

import kotlinx.serialization.Serializable

/**
 * Google Calendar 事件类型（events.list 的 `eventType` / `eventTypes` 取值）。
 * 文档取值：default / outOfOffice / focusTime / workingLocation / birthday / fromGmail。
 *
 * [apiValue] 为 API 原始字符串；[UNKNOWN] 用于向前兼容未来新增的类型。
 */
@Serializable
enum class CalendarEventType(val apiValue: String) {
    /** 常规活动。 */
    DEFAULT("default"),

    /** 不在办公室。 */
    OUT_OF_OFFICE("outOfOffice"),

    /** 专注时间。 */
    FOCUS_TIME("focusTime"),

    /** 工作地点。 */
    WORKING_LOCATION("workingLocation"),

    /** 每年重复出现的全天特别活动（生日）。 */
    BIRTHDAY("birthday"),

    /** 来自 Gmail 的活动。 */
    FROM_GMAIL("fromGmail"),

    /** 未识别的新类型（向前兼容）。 */
    UNKNOWN("");

    companion object {
        /** 从 API 字符串解析：null/空视为 [DEFAULT]，无法识别归 [UNKNOWN]。 */
        fun fromApi(raw: String?): CalendarEventType {
            if (raw.isNullOrBlank()) return DEFAULT
            return values().firstOrNull { it.apiValue == raw } ?: UNKNOWN
        }
    }
}
