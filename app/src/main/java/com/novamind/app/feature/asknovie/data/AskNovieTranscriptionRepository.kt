package com.novamind.app.feature.asknovie.data

import com.novamind.app.common.config.AppConfig
import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.NetworkModule
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
            val partialTexts = extractTranscriptionSteps(data?.text.orEmpty())
            val finalText = partialTexts.lastOrNull().orEmpty()
            AppLog.i(TAG) {
                "语音转写回填 steps=${partialTexts.size} finalText=$finalText"
            }
            VoiceTranscription(text = finalText, partialTexts = partialTexts)
        }
    }

    /**
     * 后端的 text 可能是普通文字，也可能是 JSON 字符串：
     * `sentences[].sentence.text`。返回全部有效的累计转写结果，最后一项为最终文字。
     */
    private fun extractTranscriptionSteps(raw: String): List<String> {
        val root = runCatching { NetworkModule.json.parseToJsonElement(raw) }.getOrNull()
            ?: return listOf(raw).filter { it.isNotEmpty() }
        return extractTextValues(root)
    }

    private fun extractTextValues(element: JsonElement): List<String> = when (element) {
        is JsonObject -> (
            element["sentences"]
                ?: element["sentence"]
                ?: element["text"]
            )?.let(::extractTextValues).orEmpty()
        is JsonArray -> element.flatMap(::extractTextValues)
        is JsonPrimitive -> {
            val value = element.contentOrNull ?: return emptyList()
            val nested = runCatching { NetworkModule.json.parseToJsonElement(value) }.getOrNull()
            if (nested != null && nested !is JsonPrimitive) {
                extractTextValues(nested)
            } else {
                listOf(value).filter { it.isNotEmpty() }
            }
        }
    }
}
