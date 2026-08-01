package com.novamind.app.data.calendar

import com.novamind.app.common.net.BindCalendarNoteRequestDto
import com.novamind.app.common.net.CreateNoteRequestDto
import com.novamind.app.common.net.NetworkModule
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.apiCall
import com.novamind.app.common.net.response.map
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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

    /**
     * 首页 meeting 一键创建笔记并绑定。后端在一个事务内完成两次写入；同一 [calendarId]
    * 已绑定时直接返回原笔记，因此网络超时后可安全重试。
     */
    suspend fun createNoteAndBind(calendarId: String, title: String?): ApiResult<MeetingNoteResult?> {
        return apiCall {
            NetworkModule.calendarBackendApi.createNoteAndBind(
                calendarId = calendarId,
                request = CreateNoteRequestDto(
                    title = title,
                    content = JsonObject(mapOf("body" to JsonPrimitive(""))),
                    preview = null,
                ),
            )
        }.map { result -> result?.let { MeetingNoteResult(noteId = it.noteId, created = it.created) } }
    }
}

data class MeetingNoteResult(
    val noteId: String,
    val created: Boolean,
)
