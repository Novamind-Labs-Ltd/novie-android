package com.novamind.app.data.calendar

import com.novamind.app.common.net.BindCalendarNoteRequestDto
import com.novamind.app.common.net.NetworkModule
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.apiCall
import com.novamind.app.common.net.response.map
import javax.inject.Inject

/** 首页会议与笔记关联仓库，统一消费后端响应信封。 */
class CalendarNoteRepository @Inject constructor() {

    suspend fun getNoteId(calendarId: String): ApiResult<String?> =
        apiCall { NetworkModule.calendarBackendApi.getNoteBinding(calendarId) }
            .map { binding -> binding?.noteId }

    suspend fun bindNote(calendarId: String, noteId: String): ApiResult<String?> =
        apiCall {
            NetworkModule.calendarBackendApi.bindNote(
                calendarId = calendarId,
                request = BindCalendarNoteRequestDto(noteId),
            )
        }.map { binding -> binding?.noteId }
}
