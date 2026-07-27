package com.novamind.app.data.calendar

import com.novamind.app.common.net.EventNoteBindDto
import com.novamind.app.common.net.NetworkModule
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.apiCall
import com.novamind.app.common.net.response.map
import java.time.Instant
import javax.inject.Inject

/** 首页会议与笔记关联仓库，统一消费后端响应信封。 */
class CalendarNoteRepository @Inject constructor() {

    suspend fun linkedNoteIds(from: Instant, to: Instant): ApiResult<Map<String, String>> =
        apiCall {
            NetworkModule.calendarBackendApi.listEvents(from.toString(), to.toString())
        }.map { events ->
            events.orEmpty().mapNotNull { event ->
                event.noteId?.let { noteId -> event.externalEventId to noteId }
            }.toMap()
        }

    suspend fun createOrGetNote(eventId: String): ApiResult<EventNoteBindDto> =
        apiCall { NetworkModule.calendarBackendApi.createOrGetNote(eventId) }
}
