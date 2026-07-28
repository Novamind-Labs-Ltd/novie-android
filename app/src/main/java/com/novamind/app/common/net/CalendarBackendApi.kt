package com.novamind.app.common.net

import com.novamind.app.common.net.response.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.PUT

/** My Novie 后端日历接口：读取与写入本地会议-笔记绑定。 */
interface CalendarBackendApi {

    @GET("api/v1.0/calendar/events/{calendarId}/note")
    suspend fun getNoteBinding(
        @Path("calendarId") calendarId: String,
    ): Response<ApiResponse<CalendarNoteBindingDto>>

    @PUT("api/v1.0/calendar/events/{calendarId}/note")
    suspend fun bindNote(
        @Path("calendarId") calendarId: String,
        @Body request: BindCalendarNoteRequestDto,
    ): Response<ApiResponse<CalendarNoteBindingDto>>
}

@Serializable
data class CalendarNoteBindingDto(
    val noteId: String? = null,
)

@Serializable
data class BindCalendarNoteRequestDto(
    val noteId: String,
)
