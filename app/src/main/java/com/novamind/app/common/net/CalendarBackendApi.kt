package com.novamind.app.common.net

import com.novamind.app.common.net.response.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** My Novie 后端日历接口：读取事件绑定状态，以及从事件创建/复用关联笔记。 */
interface CalendarBackendApi {

    @GET("api/v1.0/calendar/events")
    suspend fun listEvents(
        @Query("from") from: String,
        @Query("to") to: String,
    ): Response<ApiResponse<List<BackendCalendarEventDto>>>

    @POST("api/v1.0/calendar/events/{eventId}/note")
    suspend fun createOrGetNote(
        @Path("eventId") eventId: String,
    ): Response<ApiResponse<EventNoteBindDto>>
}

@Serializable
data class BackendCalendarEventDto(
    val externalEventId: String,
    val noteId: String? = null,
)

@Serializable
data class EventNoteBindDto(
    val noteId: String,
    val created: Boolean = false,
)
