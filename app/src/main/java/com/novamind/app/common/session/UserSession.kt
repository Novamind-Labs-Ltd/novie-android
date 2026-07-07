package com.novamind.app.common.session

/**
 * 全局登录用户档案（来自后端 `/api/v1.0/me`）。纯 Kotlin，不带序列化/框架类型（遵循 data-layering）。
 */
data class UserProfile(
    val userId: String,
    val displayName: String?,
    val email: String?,
    val avatarUrl: String?,
)

/**
 * 全局用户会话：认证态 + 档案的合并视图，全 App 单一数据源（见 user-session-design.md）。
 *
 * @property status     认证态（状态机见设计文档 §五）。
 * @property userKey     登录账户 key（邮箱），登录即刻可得、不依赖 `/me`；用于「登录账户 ↔ 日历绑定」
 *                       软一致性校验。游客/未登录为 null。收敛自旧 `AppUserProvider.userKey`。
 * @property registered 后端是否已建档；`false` 表示已登录但 Novie 侧未建档，需引导完善资料。
 * @property profile     已注册用户档案（含头像 avatarUrl，以后端为准）；未登录/未建档为 null。
 */
data class UserSession(
    val status: AuthStatus,
    val userKey: String? = null,
    val registered: Boolean = false,
    val profile: UserProfile? = null,
) {
    val isLoggedIn: Boolean get() = status == AuthStatus.AUTHENTICATED
    val isGuest: Boolean get() = status == AuthStatus.GUEST
}

/** 认证态状态机取值。 */
enum class AuthStatus { UNKNOWN, CHECKING, GUEST, UNAUTHENTICATED, AUTHENTICATED }
