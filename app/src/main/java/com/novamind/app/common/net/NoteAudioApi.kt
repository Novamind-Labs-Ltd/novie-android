package com.novamind.app.common.net

import com.novamind.app.common.net.response.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 笔记源录音上传接口（对齐后端 `doc/frontend-api.md` §7，X lane，断点续传）。
 * 流程：initiate → 循环(chunk-target + 直传该片) → complete。直传该片复用 [FilesApi.uploadPut]。
 * 一条笔记只能有一条源录音；complete 后自动触发转写（结果走 §9）。均需 JWT，走统一响应信封（abort 为 204）。
 */
interface NoteAudioApi {

    /** 发起上传会话，返回 uploadId 与每片大小。 */
    @POST("api/v1.0/notes/{noteId}/audio/initiate")
    suspend fun initiate(
        @Path("noteId") noteId: String,
        @Body body: InitiateAudioReq,
    ): Response<ApiResponse<InitiateAudioResp>>

    /** 取某分片的直传目标（签名 URL + headers）。 */
    @POST("api/v1.0/notes/{noteId}/audio/{uploadId}/chunk-target")
    suspend fun chunkTarget(
        @Path("noteId") noteId: String,
        @Path("uploadId") uploadId: String,
        @Body body: ChunkTargetReq,
    ): Response<ApiResponse<ChunkTargetResp>>

    /** 查上传进度（断点续传用）：已提交字节数 + 已成功分片序号。 */
    @GET("api/v1.0/notes/{noteId}/audio/{uploadId}/state")
    suspend fun state(
        @Path("noteId") noteId: String,
        @Path("uploadId") uploadId: String,
    ): Response<ApiResponse<AudioStateResp>>

    /** 完成上传并触发转写；返回 jobId（幂等重复完成可能为 null）。 */
    @POST("api/v1.0/notes/{noteId}/audio/{uploadId}/complete")
    suspend fun complete(
        @Path("noteId") noteId: String,
        @Path("uploadId") uploadId: String,
        @Body body: CompleteAudioReq,
    ): Response<ApiResponse<CompleteAudioResp>>

    /** 取消上传（清理已传分片，不影响笔记）。成功 HTTP 204。 */
    @POST("api/v1.0/notes/{noteId}/audio/{uploadId}/abort")
    suspend fun abort(
        @Path("noteId") noteId: String,
        @Path("uploadId") uploadId: String,
    ): Response<ApiResponse<Unit>>
}

@Serializable
data class InitiateAudioReq(
    val contentType: String,
    val totalSizeBytes: Long,
    val declaredDurationMs: Long,
)

@Serializable
data class InitiateAudioResp(
    val uploadId: String,
    val chunkSize: Long,
)

@Serializable
data class ChunkTargetReq(
    val chunkIndex: Int,
    val size: Long,
)

@Serializable
data class ChunkTargetResp(
    val chunkIndex: Int,
    val method: String? = null,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class AudioStateResp(
    val committedBytes: Long = 0,
    val committedChunks: List<Int> = emptyList(),
)

/** 分片确认：片序号 + 对象存储返回的 ETag（可能为 null）。 */
@Serializable
data class AudioChunkAck(
    val chunkIndex: Int,
    val etag: String? = null,
)

@Serializable
data class CompleteAudioReq(
    val acks: List<AudioChunkAck>,
)

@Serializable
data class CompleteAudioResp(
    val jobId: String? = null,
)
