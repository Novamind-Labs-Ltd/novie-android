package com.novamind.app.data.calendar

import java.time.LocalDate

/**
 * Google 日历数据仓库（只读）。授权 token 由 [com.novamind.app.common.google.GoogleTokenProvider] 提供。
 */
interface GoogleCalendarRepository {
    /** 拉取 [date] 当天主日历的事件，已按开始时间排序、转换为本地时区时间。 */
    suspend fun eventsOn(date: LocalDate): List<CalendarEvent>
}

/** Google access token 失效（HTTP 401）时抛出，可静默续期。 */
class GoogleAuthExpiredException(message: String = "Google authorization expired") : Exception(message)

/** Google 授权被撤销（HTTP 403）时抛出，需用户重新走同意流程。 */
class GoogleAuthRevokedException(message: String = "Google authorization revoked") : Exception(message)
