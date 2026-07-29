package com.novamind.app.feature.create.data

import com.novamind.app.common.net.NetworkModule
import com.novamind.app.common.net.PolishRequestDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

object PolishRepository {
    @Serializable
    private data class PolishError(val code: String? = null)

    suspend fun polish(request: PolishRequestDto): Result<String> = runCatching {
        val response = NetworkModule.polishApi.polish(request)
        if (response.isSuccessful) {
            response.body()?.polished?.takeIf { it.isNotBlank() }
                ?: error("Polish returned an empty result")
        } else {
            val code = response.errorBody()?.string()?.let { raw ->
                runCatching { NetworkModule.json.decodeFromString<PolishError>(raw).code }.getOrNull()
            }
            error(errorMessage(response.code(), code))
        }
    }

    private fun errorMessage(status: Int, code: String?): String = when (code) {
        "empty_note" -> "There is no text to polish"
        "note_too_long" -> "This note is too long to polish"
        "exhausted_personal_wallet", "plan_blocked" -> "AI usage limit reached"
        "not_provisioned" -> "Account setup is not complete"
        "not_registered" -> "Please sign in again"
        "plan_check_unavailable", "scope_unavailable", "polish_failed" ->
            "Polish is temporarily unavailable"
        else -> "Polish failed (HTTP $status)"
    }
}
