package com.novamind.app.feature.auth

import com.novamind.app.common.net.NetworkModule
import java.util.UUID

/**
 * 认证/资料仓库：封装 auth 域接口，向上只暴露领域模型。
 * Authorization 头由 [com.novamind.app.common.net.AuthInterceptor] 自动附加。
 */
class ProfileRepository {

    /**
     * 校验 token 并取当前用户 claims，映射为轻量 [AuthUser]。
     * 非 2xx 或 schema mismatch（如缺 sub 导致反序列化失败）一律返回 null——
     * 与后端文档约定的前端行为一致。
     */
    suspend fun fetchAuthMe(): AuthUser? =
        runCatching { NetworkModule.authApi.me(traceId = UUID.randomUUID().toString()) }
            .getOrNull()
            ?.takeIf { it.isSuccessful }
            ?.body()
            ?.let { dto ->
                AuthUser(
                    sub = dto.sub,
                    email = dto.email,
                    // name 优先用 name，否则用 given/family 拼接，再否则留空
                    name = dto.name
                        ?: listOfNotNull(dto.givenName, dto.familyName)
                            .joinToString(" ")
                            .ifBlank { null },
                    picture = dto.picture,
                    emailVerified = dto.emailVerified,
                )
            }
}
