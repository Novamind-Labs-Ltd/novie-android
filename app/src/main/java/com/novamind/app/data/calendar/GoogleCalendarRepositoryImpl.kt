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
            AppLog.d(TAG) { "eventsOn 请求: calendarId=primary date=$date zone=$zoneId timeMin=$timeMin timeMax=$timeMax" }
            try {
                val resp = api.listEvents(calendarId = "primary", timeMin = timeMin, timeMax = timeMax)
                // 日历级默认提醒：事件 reminders.useDefault=true 时的实际提醒来源。
                val calendarDefaults = resp.defaultReminders.mapNotNull { it.toDomainReminder() }
                val items = resp.items
                val active = items.filter { it.status != "cancelled" }
                val mapped = active.mapNotNull { it.toDomain(calendarDefaults) }
                // 仅展示常规活动与专注时间，其余类型（外出/工作地点/生日/Gmail 等）不进列表。
                val shown = mapped.filter { it.isMeeting }.sortedBy { it.start }
                // 各过滤阶段计数，便于定位「Google 有数据但列表空」是被哪一步过滤掉的。
                AppLog.d(TAG) { "eventsOn 结果: date=$date raw=${items.size} active=${active.size} " +
                        "mapped=${mapped.size} shown(isMeeting)=${shown.size}" }
                shown
            } catch (e: HttpException) {
                AppLog.w(TAG, e) { "eventsOn HTTP 错误: date=$date code=${e.code()}" }
                throw e.toAuthAware()
            }
        }

    override suspend fun updateEvent(event: CalendarEvent): CalendarEvent =
        withContext(Dispatchers.IO) {
            // location/description 用空串而非 null 提交：允许「清空」；null 会被 explicitNulls=false 略过。
            val body = EventPatchDto(
                summary = event.title,
                location = event.location.orEmpty(),
                description = event.description.orEmpty(),
                start = event.start.toApiDateTime(event.isAllDay),
                // 全天事件 end.date 为排他次日；定时事件直接用结束时刻。
                end = if (event.isAllDay) {
                    event.end.toLocalDate().plusDays(1).toApiDate()
                } else {
                    event.end.toApiDateTime(false)
                },
            )
            AppLog.d(TAG) { "patchEvent id=${event.id} body=$body" }
            try {
                api.patchEvent(calendarId = "primary", eventId = event.id, body = body)
                    .toDomain() ?: event
            } catch (e: HttpException) {
                throw e.toAuthAware()
            }
        }

    /** LocalDateTime → API 时间：定时事件带时区偏移的 RFC3339；全天事件取日期。 */
    private fun LocalDateTime.toApiDateTime(allDay: Boolean): EventDateTimeDto =
        if (allDay) {
            toLocalDate().toApiDate()
        } else {
            EventDateTimeDto(
                dateTime = atZone(zoneId).toOffsetDateTime().format(RFC3339),
                timeZone = zoneId.id,
            )
        }

    private fun LocalDate.toApiDate(): EventDateTimeDto = EventDateTimeDto(date = toString())

    /** 把鉴权类 HTTP 错误转成领域异常：401→过期可续期，403→被撤销需重新同意。其余原样抛出。 */
    private fun HttpException.toAuthAware(): Throwable = when (code()) {
        HttpURLConnection.HTTP_UNAUTHORIZED -> GoogleAuthExpiredException()
        HttpURLConnection.HTTP_FORBIDDEN -> GoogleAuthRevokedException()
        else -> this
    }

    private fun EventDto.toDomain(calendarDefaults: List<CalendarReminder> = emptyList()): CalendarEvent? {
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
        // 会议链接：优先 hangoutLink，其次 conferenceData 里 video 类型入口的 uri。
        val meetingUrl = hangoutLink?.takeIf { it.isNotBlank() }
            ?: conferenceData?.entryPoints
                ?.firstOrNull { it.entryPointType == "video" && !it.uri.isNullOrBlank() }
                ?.uri
        val hasMeetingLink = !meetingUrl.isNullOrBlank() ||
                !conferenceData?.conferenceId.isNullOrBlank()
        // 生效提醒：无 reminders 视为无；useDefault=true 用日历默认；否则用事件自身 overrides。
        val effectiveReminders = when {
            reminders == null -> emptyList()
            reminders.useDefault -> calendarDefaults
            else -> reminders.overrides.mapNotNull { it.toDomainReminder() }
        }
        val event = CalendarEvent(
            id = id,
            title = summary?.takeIf { it.isNotBlank() } ?: "(No title)",
            isAllDay = isAllDay,
            start = startLocal,
            end = endLocal,
            location = location,
            description = description?.takeIf { it.isNotBlank() },
            eventType = domainType,
            isMeeting = domainType == CalendarEventType.DEFAULT && (hasInvitees || hasMeetingLink),
            attendees = attendees.map {
                CalendarAttendee(
                    email = it.email,
                    displayName = it.displayName,
                    self = it.self,
                    responseStatus = AttendeeResponse.fromApi(it.responseStatus),
                )
            },
            reminders = effectiveReminders,
            meetingUrl = meetingUrl,
        )
        // 映射结果（字段对应）：
        // id<-id, title<-summary, isAllDay<-(start.dateTime==null&&start.date!=null),
        // start<-start.(dateTime|date), end<-end.(dateTime|date)?:start,
        // location<-location, eventType<-eventType（仅信息展示，不再区分会议/任务），
        // isMeeting<-eventType==default&&(attendees 有他人||hangoutLink/conferenceId 非空)
        AppLog.d(TAG) { "  -> CalendarEvent: id=${event.id} title=${event.title} isAllDay=${event.isAllDay} " +
                    "start=${event.start} end=${event.end} location=${event.location} " +
                    "eventType=${event.eventType} isMeeting=${event.isMeeting} attendees=${event.attendees.size} " +
                    "reminders=${event.reminders.joinToString { "${it.minutesBefore}m/${it.method}" }} " +
                    "meetingUrl=${event.meetingUrl}" }
        return event
    }

    private fun EventDateTimeDto.toLocalDateTime(): LocalDateTime? = when {
        dateTime != null -> OffsetDateTime.parse(dateTime)
            .atZoneSameInstant(zoneId)
            .toLocalDateTime()

        date != null -> LocalDate.parse(date).atStartOfDay()
        else -> null
    }

    /** 提醒 DTO → 领域模型；minutes 缺省则丢弃该条。 */
    private fun ReminderOverrideDto.toDomainReminder(): CalendarReminder? =
        minutes?.let { CalendarReminder(minutesBefore = it, method = ReminderMethod.fromApi(method)) }

    private companion object {
        const val TAG = "CalendarRepo"
        val RFC3339: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    }
}
