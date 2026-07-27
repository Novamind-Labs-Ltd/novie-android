package com.novamind.app.feature.asknovie.data

import com.novamind.app.common.config.AppConfig
import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.NetworkModule
import com.novamind.app.common.net.TranscribeData
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.apiCall
import com.novamind.app.common.net.response.map
import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

object AskNovieTranscriptionRepository {
    private const val TAG = "AskNovieTranscription"

    suspend fun transcribe(path: String, durationSeconds: Int): ApiResult<TranscribeData> {
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
            val parsedText = extractTranscriptionText(data?.text.orEmpty())
            AppLog.i(TAG) { "语音转写回填文字 text=$parsedText" }
            (data ?: TranscribeData()).copy(text = parsedText)
        }
    }

    /**
     * 后端的 text 可能是普通文字，也可能是 JSON 字符串：
     * `sentences[].sentence.text`。输入框只接收数组最后一个有效 sentence.text。
     */
    private fun extractTranscriptionText(raw: String): String {
        val root = runCatching { NetworkModule.json.parseToJsonElement(raw) }.getOrNull()
            ?: return raw
        return extractTextValue(root).orEmpty()
    }

    private fun extractTextValue(element: JsonElement): String? = when (element) {
        is JsonObject -> (
            element["sentences"]
                ?: element["sentence"]
                ?: element["text"]
            )?.let(::extractTextValue)
        is JsonArray -> element.asReversed().firstNotNullOfOrNull { item ->
            extractTextValue(item)?.takeIf { it.isNotEmpty() }
        }
        is JsonPrimitive -> {
            val value = element.contentOrNull ?: return null
            val nested = runCatching { NetworkModule.json.parseToJsonElement(value) }.getOrNull()
            if (nested != null && nested !is JsonPrimitive) extractTextValue(nested) else value
        }
    }
}
