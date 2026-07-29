package com.novamind.app.common.net

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class PolishSelectionDto(
    val before: String,
    val target: String,
    val after: String,
)

@Serializable
data class PolishRequestDto(
    val text: String? = null,
    val selection: PolishSelectionDto? = null,
)

@Serializable
data class PolishResponseDto(val polished: String)

/** Agent 的一次性 AI 润色接口；响应为裸 JSON，不使用后端统一信封。 */
interface PolishApi {
    @POST("v1/polish")
    suspend fun polish(@Body body: PolishRequestDto): Response<PolishResponseDto>
}
