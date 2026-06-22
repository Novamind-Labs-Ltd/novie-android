package com.novamind.app.feature.auth

import com.novamind.app.common.net.MeDto
import com.novamind.app.common.net.NetworkModule

/**
 * 用户资料仓库：封装 auth 域接口调用，向上只暴露领域结果（成功取 body，失败/非 2xx 返回 null）。
 * Authorization 头由 [com.novamind.app.common.net.AuthInterceptor] 自动附加。
 */
class ProfileRepository {

    /** 拉取当前登录用户信息；失败或未鉴权返回 null。 */
    suspend fun fetchMe(): MeDto? =
        runCatching { NetworkModule.authApi.me() }
            .getOrNull()
            ?.takeIf { it.isSuccessful }
            ?.body()
}
