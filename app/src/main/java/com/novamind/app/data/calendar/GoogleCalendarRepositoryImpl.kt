package com.novamind.app.data.calendar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.net.HttpURLConnection
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * [GoogleCalendarRepository] 默认实现：调用 Calendar v3 REST API 并映射为领域模型。
 *
 * @param api 默认走 [GoogleCalendarNetwork.api]。
 * @param zoneId 时区，默认系统时区；查询区间与时间换算都以它为准。
 */
class GoogleCalendarRepositoryImpl(
    private val api: GoogleCalendarApi = GoogleCalendarNetwork.api,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : GoogleCalendarRepository {

    override suspend fun eventsOn(date: LocalDate): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val timeMin = date.atStartOfDay(zoneId).toOffsetDateTime().format(RFC3339)
        val timeMax = date.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime().format(RFC3339)
        try {
            api.listEvents(calendarId = "primary", timeMin = timeMin, timeMax = timeMax)
                .items
                .filter { it.status != "cancelled" }
                .mapNotNull { it.toDomain() }
                .sortedBy { it.start }
        } catch (e: HttpException) {
            throw e.toAuthAware()
        }
    }

    override suspend fun currentAccountEmail(): String = withContext(Dispatchers.IO) {
        try {
            api.getCalendar(calendarId = "primary").id
                ?: throw IllegalStateException("primary calendar has no id")
        } catch (e: HttpException) {
            throw e.toAuthAware()
        }
    }

    /** 把鉴权类 HTTP 错误转成领域异常：401→过期可续期，403→被撤销需重新同意。其余原样抛出。 */
    private fun HttpException.toAuthAware(): Throwable = when (code()) {
        HttpURLConnection.HTTP_UNAUTHORIZED -> GoogleAuthExpiredException()
        HttpURLConnection.HTTP_FORBIDDEN -> GoogleAuthRevokedException()
        else -> this
    }

    private fun EventDto.toDomain(): CalendarEvent? {
        val id = id ?: return null
        val startDt = start ?: return null
        val isAllDay = startDt.dateTime == null && startDt.date != null
        val startLocal = startDt.toLocalDateTime() ?: return null
        val endLocal = end?.toLocalDateTime() ?: startLocal
        val hasOtherAttendees = attendees.any { !it.self }
        val isMeeting = hasOtherAttendees || hangoutLink != null || conferenceData != null
        return CalendarEvent(
            id = id,
            title = summary?.takeIf { it.isNotBlank() } ?: "(No title)",
            isAllDay = isAllDay,
            start = startLocal,
            end = endLocal,
            location = location,
            isMeeting = isMeeting,
        )
    }

    private fun EventDateTimeDto.toLocalDateTime(): LocalDateTime? = when {
        dateTime != null -> OffsetDateTime.parse(dateTime)
            .atZoneSameInstant(zoneId)
            .toLocalDateTime()
        date != null -> LocalDate.parse(date).atStartOfDay()
        else -> null
    }

    private companion object {
        val RFC3339: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    }
}
