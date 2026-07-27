package com.novamind.app.feature.asknovie.data

import com.novamind.app.common.net.NetworkModule
import com.novamind.app.common.net.TranscribeData
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.apiCall
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

object AskNovieTranscriptionRepository {
    suspend fun transcribe(path: String, durationSeconds: Int): ApiResult<TranscribeData> {
        val file = File(path)
        if (!file.isFile) {
            return ApiResult.NetworkError(message = "Recorded audio file is missing")
        }
        val audio = MultipartBody.Part.createFormData(
            name = "audio",
            filename = file.name,
            body = file.asRequestBody("audio/aac".toMediaType()),
        )
        val declaredDuration = (durationSeconds * 1_000L)
            .toString()
            .toRequestBody("text/plain".toMediaType())
        return apiCall {
            NetworkModule.askNovieTranscribeApi.transcribe(audio, declaredDuration)
        }
    }
}
