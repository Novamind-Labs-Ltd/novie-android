package com.novamind.app.common.net

import com.novamind.app.common.net.response.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class ConversationAttachRequest(val fileId: String)

@Serializable
data class ConversationAttachmentDto(
    val fileId: String,
    val conversationId: String,
    val kind: String,
    val status: String,
    val originalFilename: String? = null,
    val sizeBytes: Long? = null,
    val downloadUrl: String? = null,
    val expiresAt: String? = null,
)

/** Ask Novie 会话级临时附件：文件先经 files 上传，再关联到 conversation。 */
interface ConversationAttachmentsApi {
    @POST("api/v1.0/conversations/{conversationId}/attachments")
    suspend fun attach(
        @Path("conversationId") conversationId: String,
        @Body body: ConversationAttachRequest,
    ): Response<ApiResponse<ConversationAttachmentDto>>

    @GET("api/v1.0/conversations/{conversationId}/attachments")
    suspend fun list(
        @Path("conversationId") conversationId: String,
    ): Response<ApiResponse<List<ConversationAttachmentDto>>>
}
