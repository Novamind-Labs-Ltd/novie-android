package com.novamind.app.feature.auth

import com.novamind.app.common.log.AppLog
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
    suspend fun fetchAuthMe(): AuthUser? {
        val traceId = UUID.randomUUID().toString()
        val resp = runCatching { NetworkModule.authApi.me(traceId = traceId) }
            .onFailure {
                // 网络异常或反序列化失败（如缺 sub 的 schema mismatch）
                AppLog.w(TAG) { "/api/auth/me 请求失败 trace-id=$traceId: ${it.message}" }
            }
            .getOrNull() ?: return null

        if (!resp.isSuccessful) {
            AppLog.w(TAG) { "/api/auth/me 非2xx code=${resp.code()} trace-id=$traceId" }
            return null
        }
        val dto = resp.body()
        if (dto == null) {
            AppLog.w(TAG) { "/api/auth/me 响应体为空 trace-id=$traceId" }
            return null
        }
        AppLog.i(TAG) { "/api/auth/me 成功 sub=${dto.sub} trace-id=$traceId" }
        return AuthUser(
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

    private companion object {
        const val TAG = "AuthMe"
    }
}
