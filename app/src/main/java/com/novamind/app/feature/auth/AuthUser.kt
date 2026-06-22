package com.novamind.app.feature.auth

/**
 * 轻量认证用户（来自 /api/auth/me 的 OIDC claims）。
 * 这不是完整的 Novie 档案——完整档案需另外请求 /api/v1.0/members/me。
 */
data class AuthUser(
    val sub: String,
    val email: String?,
    val name: String?,
    val picture: String?,
    val emailVerified: Boolean?,
)
