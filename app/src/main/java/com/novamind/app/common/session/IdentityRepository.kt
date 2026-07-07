package com.novamind.app.common.session

import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.MeProfileDto
import com.novamind.app.common.net.NetworkModule
import com.novamind.app.common.net.response.ApiResponse
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.apiCall
import retrofit2.Response

/**
 * 身份档案仓库：封装 `/api/v1.0/me` 调用与 DTO→领域映射，向上只暴露领域三态。
 *
 * 逻辑下沉到此（可注入、可替身）而非 [UserSessionManager]，让 manager 保持薄壳。
 * 构造带默认参数、无 Hilt 依赖，既可被 [UserSessionManager]/`AuthViewModel` 直接实例化
 * （仿 `ProfileRepository`），也便于单测传入自定义 [meCall] 替身。
 *
 * @param meCall 发起 `/me` 请求的挂起函数，默认走 [NetworkModule.identityApi]。
 */
class IdentityRepository(
    private val meCall: suspend () -> Response<ApiResponse<MeProfileDto>> = {
        NetworkModule.identityApi.me()
    },
) {

    /**
     * 拉取后端档案并映射为 [Outcome]。
     * - 成功且有 data → [Outcome.Profile]（registered 以 DTO 为准，缺 userId 时视为未建档）。
     * - 成功但 204/空 data → [Outcome.NotRegistered]（已登录未建档）。
     * - 鉴权失效（401/业务码）→ [Outcome.Unauthorized]。
     * - 其它业务/网络错误 → [Outcome.Failed]（保留缓存，不改认证态）。
     */
    suspend fun fetchProfile(): Outcome = when (val r = apiCall { meCall() }) {
        is ApiResult.Success -> {
            val dto = r.data
            val userId = dto?.userId
            when {
                dto == null -> Outcome.NotRegistered
                !dto.registered || userId.isNullOrBlank() -> Outcome.NotRegistered
                else -> Outcome.Profile(
                    UserProfile(
                        userId = userId,
                        displayName = dto.displayName,
                        email = dto.email,
                        avatarUrl = dto.avatarUrl,
                    ),
                )
            }
        }

        is ApiResult.BizError -> {
            if (r.isAuthExpired) {
                AppLog.w(TAG) { "/me 鉴权失效 code=${r.code}" }
                Outcome.Unauthorized
            } else {
                AppLog.w(TAG) { "/me 业务错误 code=${r.code} msg=${r.message}" }
                Outcome.Failed
            }
        }

        is ApiResult.NetworkError -> {
            AppLog.w(TAG) { "/me 网络错误: ${r.message}" }
            Outcome.Failed
        }
    }

    /** `/me` 拉取结果的领域映射。 */
    sealed interface Outcome {
        /** 已建档，携带档案。 */
        data class Profile(val profile: UserProfile) : Outcome
        /** 已登录但后端未建档（204 / registered=false）。 */
        data object NotRegistered : Outcome
        /** 鉴权失效，应转未登录并重登。 */
        data object Unauthorized : Outcome
        /** 业务/网络失败，保留本地缓存兜底。 */
        data object Failed : Outcome
    }

    private companion object {
        const val TAG = "Identity"
    }
}
