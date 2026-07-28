package com.novamind.app.feature.asknovie.data

import com.novamind.app.common.config.AppConfig
import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.NetworkModule
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.apiCall
import com.novamind.app.common.net.response.map
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

object AskNovieTranscriptionRepository {
    private const val TAG = "AskNovieTranscription"

    data class VoiceTranscription(
        val text: String,
        val partialTexts: List<String>,
    )

    suspend fun transcribe(path: String, durationSeconds: Int): ApiResult<VoiceTranscription> {
        val file = File(path)
        if (!file.isFile) {
            return ApiResult.NetworkError(message = "Recorded audio file is missing")
        }
        val audio = MultipartBody.Part.createFormData(
            name = "audio",
            filename = file.name,
            body = file.asRequestBody(AppConfig.Media.AUDIO_MIME.toMediaType()),
        )
        val declaredDuration = (durationSeconds * 1_000L)
            .toString()
            .toRequestBody("text/plain".toMediaType())
        return apiCall {
            NetworkModule.askNovieTranscribeApi.transcribe(audio, declaredDuration)
        }.map { data ->
            // Retrofit 已解出统一响应的 data，text 即最终转写文字。
            val finalText = data?.text.orEmpty().trim()
            val partialTexts = listOf(finalText).filter { it.isNotEmpty() }
            AppLog.i(TAG) {
                "语音转写回填 steps=${partialTexts.size} finalText=$finalText"
            }
            VoiceTranscription(text = finalText, partialTexts = partialTexts)
        }
    }
}
