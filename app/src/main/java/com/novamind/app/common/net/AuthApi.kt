package com.novamind.app.common.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers

/**
 * 认证接口（现统一走 api 域）。baseUrl 取自 [ApiConfig.apiBaseUrl]（以 "/" 结尾），
 * 故路径用相对形式、不带前导斜杠。Authorization 头由 [AuthInterceptor] 统一附加。
 */
interface AuthApi {

    /**
     * 校验 Auth0 access token 并返回 OIDC claims（轻量用户，非完整 Novie 档案）。
     * 完整档案走 /api/v1.0/members/me。Authorization 由 [AuthInterceptor] 附加；
     * 这里显式带不缓存与链路追踪头。
     *
     * @param traceId 每次请求一个 uuid，便于后端链路排查。
     */
    @Headers("Cache-Control: no-store")
    @GET("api/auth/me")
    suspend fun me(@Header("trace-id") traceId: String): Response<MeDto>
}

/**
 * /api/auth/me 响应模型，对齐后端 OIDC claims。
 * [sub] 为必填（OIDC 主体标识）；缺失会导致反序列化失败，仓库层据此返回 null（schema mismatch）。
 * JSON 解析容忍未知字段（见 [NetworkModule] 的 Json 配置）。
 */
@Serializable
data class MeDto(
    val sub: String,
    val email: String? = null,
    val name: String? = null,
    @SerialName("given_name") val givenName: String? = null,
    @SerialName("family_name") val familyName: String? = null,
    val picture: String? = null,
    @SerialName("email_verified") val emailVerified: Boolean? = null,
)
