package com.novamind.app.common.net

import com.novamind.app.common.net.response.ApiResponse
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

@Serializable
data class TranscribeData(
    val text: String = "",
    val language: String? = null,
)

/** Ask Novie 短语音同步转写：音频不持久化，结果只回填聊天输入框。 */
interface AskNovieTranscribeApi {
    @Multipart
    @POST("api/v1.0/transcribe")
    suspend fun transcribe(
        @Part audio: MultipartBody.Part,
        @Part("declaredDurationMs") declaredDurationMs: RequestBody,
    ): Response<ApiResponse<TranscribeData>>
}
