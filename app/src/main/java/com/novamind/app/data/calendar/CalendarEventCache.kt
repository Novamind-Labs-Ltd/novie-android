package com.novamind.app.data.calendar

import com.novamind.app.common.log.AppLog
import com.novamind.app.common.storage.KeyValueStore
import com.novamind.app.common.storage.MmkvStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 按账号隔离的持久化事件缓存。
 *
 * key = `accountId#date`，value = 事件 JSON。冷启动可先渲染缓存再后台刷新；
 * 缓存仅作展示加速，权威数据始终以网络拉取为准。
 *
 * 账号隔离是「切换账号不复用旧数据」的硬保证：读取只取当前绑定账号分区。
 * 由于同一时刻只绑定一个账号，断开 / 换账号 / 退出登录时整体 [clear] 即可。
 */
class CalendarEventCache(
    private val store: KeyValueStore = MmkvStore(MMAP_ID),
) {
    fun get(accountId: String, date: LocalDate): List<CalendarEvent>? {
        val raw = store.getString(key(accountId, date), null) ?: return null
        return runCatching {
            json.decodeFromString<List<CachedEvent>>(raw).map { it.toDomain() }
        }.onFailure { AppLog.w(TAG, it) { "calendar cache decode failed" } }
            .getOrNull()
    }

    fun put(accountId: String, date: LocalDate, events: List<CalendarEvent>) {
        runCatching {
            json.encodeToString(events.map { CachedEvent.fromDomain(it) })
        }.onSuccess { store.putString(key(accountId, date), it) }
            .onFailure { AppLog.w(TAG, it) { "calendar cache encode failed" } }
    }

    /** 清空全部缓存（断开 / 换账号 / 退出登录）。 */
    fun clear() {
        store.clear()
    }

    private fun key(accountId: String, date: LocalDate) = "$accountId#$date"

    @Serializable
    private data class CachedEvent(
        val id: String,
        val title: String,
        val isAllDay: Boolean,
        val start: String,
        val end: String,
        val location: String?,
        // 旧缓存无以下字段 → 默认空/false，网络刷新后回正。
        val description: String? = null,
        val eventType: CalendarEventType = CalendarEventType.DEFAULT,
        val isMeeting: Boolean = false,
        val attendees: List<CachedAttendee> = emptyList(),
        val reminders: List<CachedReminder> = emptyList(),
    ) {
        fun toDomain() = CalendarEvent(
            id = id,
            title = title,
            isAllDay = isAllDay,
            start = LocalDateTime.parse(start),
            end = LocalDateTime.parse(end),
            location = location,
            eventType = eventType,
            isMeeting = isMeeting,
            description = description,
            attendees = attendees.map { it.toDomain() },
            reminders = reminders.map { it.toDomain() },
        )

        companion object {
            fun fromDomain(e: CalendarEvent) = CachedEvent(
                id = e.id,
                title = e.title,
                isAllDay = e.isAllDay,
                start = e.start.toString(),
                end = e.end.toString(),
                location = e.location,
                description = e.description,
                eventType = e.eventType,
                isMeeting = e.isMeeting,
                attendees = e.attendees.map { CachedAttendee.fromDomain(it) },
                reminders = e.reminders.map { CachedReminder.fromDomain(it) },
            )
        }
    }

    /** 缓存参会者（与 [CalendarAttendee] 一一对应）。 */
    @Serializable
    private data class CachedAttendee(
        val email: String? = null,
        val displayName: String? = null,
        val self: Boolean = false,
        val responseStatus: AttendeeResponse = AttendeeResponse.UNKNOWN,
    ) {
        fun toDomain() = CalendarAttendee(email, displayName, self, responseStatus)

        companion object {
            fun fromDomain(a: CalendarAttendee) =
                CachedAttendee(a.email, a.displayName, a.self, a.responseStatus)
        }
    }

    /** 缓存提醒（与 [CalendarReminder] 一一对应）。 */
    @Serializable
    private data class CachedReminder(
        val minutesBefore: Int = 0,
        val method: ReminderMethod = ReminderMethod.UNKNOWN,
    ) {
        fun toDomain() = CalendarReminder(minutesBefore, method)

        companion object {
            fun fromDomain(r: CalendarReminder) = CachedReminder(r.minutesBefore, r.method)
        }
    }

    private companion object {
        const val MMAP_ID = "calendar_event_cache"
        const val TAG = "CalendarCache"
        val json = Json { ignoreUnknownKeys = true }
    }
}
