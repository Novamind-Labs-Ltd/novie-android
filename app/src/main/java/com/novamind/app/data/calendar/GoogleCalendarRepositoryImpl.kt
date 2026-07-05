package com.novamind.app.data.calendar

import com.novamind.app.common.log.AppLog
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

    override suspend fun eventsOn(date: LocalDate): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            val timeMin = date.atStartOfDay(zoneId).toOffsetDateTime().format(RFC3339)
            val timeMax = date.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime().format(RFC3339)
            try {
                api.listEvents(calendarId = "primary", timeMin = timeMin, timeMax = timeMax)
                    .items
                    .filter { it.status != "cancelled" }
                    .mapNotNull { it.toDomain() }
                    // 仅展示常规活动与专注时间，其余类型（外出/工作地点/生日/Gmail 等）不进列表。
                    .filter { it.isMeeting }
                    .sortedBy { it.start }
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
        // 打印 EventDto 原始字段，便于核对与 CalendarEvent 的映射对应关系。
        AppLog.d(TAG) { "EventDto raw: id=$id status=$status summary=$summary location=$location " +
                    "eventType=$eventType hangoutLink=$hangoutLink start=$start end=$end " +
                    "attendees=$attendees conferenceData=$conferenceData" }
        val id = id ?: return null
        val startDt = start ?: return null
        val isAllDay = startDt.dateTime == null && startDt.date != null
        val startLocal = startDt.toLocalDateTime() ?: return null
        val endLocal = end?.toLocalDateTime() ?: startLocal
        val domainType = CalendarEventType.fromApi(eventType)
        // 会议判定：常规事件 且（有除自己外的邀请人 或 有会议链接）。
        // 仅有自己（self）在 attendees 里的独立事件不算会议；链接看 hangoutLink 或 conferenceData。
        val hasInvitees = attendees.any { !it.self }
        val hasMeetingLink = !hangoutLink.isNullOrBlank() ||
                !conferenceData?.conferenceId.isNullOrBlank()
        val event = CalendarEvent(
            id = id,
            title = summary?.takeIf { it.isNotBlank() } ?: "(No title)",
            isAllDay = isAllDay,
            start = startLocal,
            end = endLocal,
            location = location,
            eventType = domainType,
            isMeeting = domainType == CalendarEventType.DEFAULT && (hasInvitees || hasMeetingLink),
        )
        // 映射结果（字段对应）：
        // id<-id, title<-summary, isAllDay<-(start.dateTime==null&&start.date!=null),
        // start<-start.(dateTime|date), end<-end.(dateTime|date)?:start,
        // location<-location, eventType<-eventType（仅信息展示，不再区分会议/任务），
        // isMeeting<-eventType==default&&(attendees 有他人||hangoutLink/conferenceId 非空)
        AppLog.d(TAG) { "  -> CalendarEvent: id=${event.id} title=${event.title} isAllDay=${event.isAllDay} " +
                    "start=${event.start} end=${event.end} location=${event.location} " +
                    "eventType=${event.eventType} isMeeting=${event.isMeeting}" }
        return event
    }

    private fun EventDateTimeDto.toLocalDateTime(): LocalDateTime? = when {
        dateTime != null -> OffsetDateTime.parse(dateTime)
            .atZoneSameInstant(zoneId)
            .toLocalDateTime()

        date != null -> LocalDate.parse(date).atStartOfDay()
        else -> null
    }

    private companion object {
        const val TAG = "CalendarRepo"
        val RFC3339: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    }
}
