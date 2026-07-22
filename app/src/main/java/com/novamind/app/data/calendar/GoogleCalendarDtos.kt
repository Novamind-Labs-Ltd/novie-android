package com.novamind.app.data.calendar

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Google Calendar API v3 `events.list` 响应的精简模型。
 * 只声明用得到的字段；[com.novamind.app.data.calendar.GoogleCalendarNetwork] 的 JSON
 * 已开启 ignoreUnknownKeys，其余字段忽略。
 *
 * 文档：https://developers.google.com/calendar/api/v3/reference/events/list
 */
@Serializable
data class EventsResponse(
    val items: List<EventDto> = emptyList(),
    val nextPageToken: String? = null,
    /** 日历级默认提醒：事件 reminders.useDefault=true 时的实际提醒时间来源（Google 在响应顶层给出）。 */
    val defaultReminders: List<ReminderOverrideDto> = emptyList(),
)

@Serializable
data class EventDto(
    val id: String? = null,
    val status: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val location: String? = null,
    val eventType: String? = null,
    val hangoutLink: String? = null,
    val start: EventDateTimeDto? = null,
    val end: EventDateTimeDto? = null,
    val attendees: List<AttendeeDto> = emptyList(),
    val conferenceData: ConferenceDataDto? = null,
    /** 提醒：useDefault=true 用日历默认（见 [EventsResponse.defaultReminders]）；否则用 overrides。 */
    val reminders: EventRemindersDto? = null,
)

@Serializable
data class EventRemindersDto(
    val useDefault: Boolean = true,
    val overrides: List<ReminderOverrideDto> = emptyList(),
)

@Serializable
data class ReminderOverrideDto(
    /** "popup" / "email"。 */
    val method: String? = null,
    /** 开始前多少分钟触发。 */
    val minutes: Int? = null,
)

/**
 * 定时事件用 [dateTime]（RFC3339，含时区偏移）；全天事件用 [date]（yyyy-MM-dd）。两者互斥。
 */
@Serializable
data class EventDateTimeDto(
    val date: String? = null,
    val dateTime: String? = null,
    val timeZone: String? = null,
)

@Serializable
data class AttendeeDto(
    val email: String? = null,
    val displayName: String? = null,
    val self: Boolean = false,
    val responseStatus: String? = null,
)

@Serializable
data class ConferenceDataDto(
    @SerialName("conferenceId") val conferenceId: String? = null,
)

/**
 * `events.patch` 请求体（精简）：只提交可编辑字段。
 * JSON 已配 explicitNulls=false，故 null 字段不序列化（PATCH 语义：不改该字段）；
 * 传空串（如清空 location/description）会真正清空对应字段。
 */
@Serializable
data class EventPatchDto(
    val summary: String? = null,
    val location: String? = null,
    val description: String? = null,
    val start: EventDateTimeDto? = null,
    val end: EventDateTimeDto? = null,
)
