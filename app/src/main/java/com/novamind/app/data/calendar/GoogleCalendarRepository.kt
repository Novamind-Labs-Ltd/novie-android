package com.novamind.app.data.calendar

import java.time.LocalDate

/**
 * Google 日历数据仓库（只读）。授权 token 由 [com.novamind.app.common.google.GoogleTokenProvider] 提供。
 */
interface GoogleCalendarRepository {
    /** 拉取 [date] 当天主日历的事件，已按开始时间排序、转换为本地时区时间。 */
    suspend fun eventsOn(date: LocalDate): List<CalendarEvent>
}

/** Google access token 失效（HTTP 401）时抛出，提示上层引导用户重新授权。 */
class GoogleAuthExpiredException(message: String = "Google authorization expired") : Exception(message)
