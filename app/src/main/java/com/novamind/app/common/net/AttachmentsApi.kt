package com.novamind.app.common.net

import com.novamind.app.common.net.response.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 笔记附件接口（对齐后端 `doc/frontend-api.md` §5 Attachments）。
 * 流程：先用 [FilesApi] 传文件拿 `fileId`（READY），再把 `fileId` 挂到笔记。附件当前仅支持图片。
 * Authorization 由 [AuthInterceptor] 统一附加，均走统一响应信封（DELETE 为 204 无体）。
 */
interface AttachmentsApi {

    /** 挂载附件：把一个 READY 文件关联到笔记。幂等（重复挂同一 fileId 仍 200）。 */
    @POST("api/v1.0/notes/{noteId}/attachments")
    suspend fun attach(
        @Path("noteId") noteId: String,
        @Body body: AttachRequestDto,
    ): Response<ApiResponse<AttachmentDto>>

    /** 列附件（每项带新鲜的签名 downloadUrl + expiresAt）。 */
    @GET("api/v1.0/notes/{noteId}/attachments")
    suspend fun list(@Path("noteId") noteId: String): Response<ApiResponse<List<AttachmentDto>>>

    /** 解除挂载。成功 HTTP 204（无响应体，绕过信封）。 */
    @DELETE("api/v1.0/notes/{noteId}/attachments/{fileId}")
    suspend fun detach(
        @Path("noteId") noteId: String,
        @Path("fileId") fileId: String,
    ): Response<ApiResponse<Unit>>
}

/** 挂载附件请求体。 */
@Serializable
data class AttachRequestDto(
    val fileId: String,
)

/** 附件视图（AttachmentView）。`kind` 当前恒为 `IMAGE`；`downloadUrl` 有时效（[expiresAt] 后需重列）。 */
@Serializable
data class AttachmentDto(
    val fileId: String,
    val kind: String? = null,
    val originalFilename: String? = null,
    val sizeBytes: Long? = null,
    val downloadUrl: String? = null,
    val expiresAt: String? = null,
)
