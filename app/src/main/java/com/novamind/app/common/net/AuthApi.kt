package com.novamind.app.common.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET

/**
 * 认证域（auth.novamind-labs.*）接口。baseUrl 取自 [ApiConfig.authBaseUrl]（以 "/" 结尾），
 * 故路径用相对形式、不带前导斜杠。Authorization 头由 [AuthInterceptor] 统一附加。
 */
interface AuthApi {

    /** 当前登录用户信息。 */
    @GET("api/auth/me")
    suspend fun me(): Response<MeDto>
}

/**
 * /api/auth/me 响应模型。字段按后端实际返回调整；JSON 解析容忍未知字段
 * （见 [NetworkModule] 的 Json 配置），故多余字段不会报错。
 */
@Serializable
data class MeDto(
    @SerialName("id") val id: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("name") val name: String? = null,
)
