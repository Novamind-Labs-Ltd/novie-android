package com.novamind.app.common.net

import com.novamind.app.common.net.response.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 转写结果 / 状态拉取接口（对齐后端 `doc/frontend-api.md` §9，E5 前端轮询）。
 * 纯拉取无推送：拿到 jobId 后轮询 [list] 直到 `presentationState = READY`，把 [TranscriptSegmentDto] 落进笔记，
 * 再调 [consume] 标记已消费（首页「Transcript ready」feed 不再提示）。均需 JWT。
 */
interface TranscriptionApi {

    /** 列出某笔记名下所有转写任务（含结果，最早在前）。走统一响应信封。 */
    @GET("api/v1.0/notes/{noteId}/transcription")
    suspend fun list(
        @Path("noteId") noteId: String,
    ): Response<ApiResponse<List<TranscriptionTaskDto>>>

    /** 标记某转写任务「已消费」。成功为 HTTP 200 无信封（幂等），故用 [Response] 判 `isSuccessful`。 */
    @POST("api/v1.0/notes/{noteId}/transcription/{jobId}/consume")
    suspend fun consume(
        @Path("noteId") noteId: String,
        @Path("jobId") jobId: String,
    ): Response<Unit>
}

// ─── DTOs ───────────────────────────────────────────────────────────────────

/** 一条转写任务。`presentationState` 取值 PROCESSING / READY / FAILED；`kind` X=源录音 / Y=听写。 */
@Serializable
data class TranscriptionTaskDto(
    val jobId: String,
    val presentationState: String,
    val resultSegments: List<TranscriptSegmentDto>? = null,   // PROCESSING 时为 null
    val consumed: Boolean = false,
    val kind: String? = null,
)

/** 转写结果分段：说话人标签 + 起止毫秒 + 文本。 */
@Serializable
data class TranscriptSegmentDto(
    val speaker: String? = null,
    val startMs: Long = 0,
    val endMs: Long = 0,
    val text: String = "",
)
