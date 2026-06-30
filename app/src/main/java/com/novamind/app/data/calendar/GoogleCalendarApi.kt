package com.novamind.app.data.calendar

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Google Calendar REST API v3（只读）。Authorization 头由 [GoogleCalendarNetwork] 的拦截器统一注入。
 * baseUrl 为 https://www.googleapis.com/calendar/v3/。
 */
interface GoogleCalendarApi {

    /**
     * 列出某日历在 [timeMin, timeMax) 区间内的事件。
     * singleEvents=true 会把循环事件展开为单次实例，配合 orderBy=startTime 按开始时间排序。
     *
     * @param calendarId 通常用 "primary" 表示当前用户主日历。
     * @param timeMin / timeMax RFC3339 时间戳（含时区偏移）。
     */
    @GET("calendars/{calendarId}/events")
    suspend fun listEvents(
        @Path("calendarId") calendarId: String,
        @Query("timeMin") timeMin: String,
        @Query("timeMax") timeMax: String,
        @Query("singleEvents") singleEvents: Boolean = true,
        @Query("orderBy") orderBy: String = "startTime",
        @Query("maxResults") maxResults: Int = 250,
    ): EventsResponse
}
