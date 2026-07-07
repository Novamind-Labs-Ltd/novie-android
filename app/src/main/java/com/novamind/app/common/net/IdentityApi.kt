package com.novamind.app.common.net

import com.novamind.app.common.net.response.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET

/**
 * 身份档案接口（后端 Novie 档案，走统一响应信封）。baseUrl 取 [ApiConfig.apiBaseUrl]（"/" 结尾），
 * 相对路径不带前导斜杠。Authorization 由 [AuthInterceptor] 自动附加。
 *
 * 注意与 [AuthApi.me]（= `/api/auth/me`，Auth0 OIDC claims）区分：
 * 本接口取后端**档案**（是否已建档 + displayName/email/avatarUrl），是全局用户会话的档案源；
 * 前者仅用于校验 Auth0 access token。
 */
interface IdentityApi {

    /**
     * 取当前登录用户的后端档案。已登录未建档时后端约定返回 204（`apiCall` 折叠为 `Success(null)`），
     * 上层据此置 `registered=false` 引导建档。
     */
    @GET("api/v1.0/me")
    suspend fun me(): Response<ApiResponse<MeProfileDto>>
}

/**
 * `/api/v1.0/me` 响应模型（信封由 `apiCall` 处理）。字段全可空/带默认，
 * JSON 解析容忍未知字段（见 [NetworkModule] 的 Json 配置）。
 */
@Serializable
data class MeProfileDto(
    val registered: Boolean = false,
    val userId: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
)
