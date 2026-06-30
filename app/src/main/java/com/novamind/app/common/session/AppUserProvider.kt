package com.novamind.app.common.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App 会话快照：登录用户 key（邮箱，游客/无 email 为 null）与是否游客。 */
data class AppSession(
    val userKey: String? = null,
    val isGuest: Boolean = false,
)

/**
 * 当前 App 登录会话的轻量身份源。
 *
 * 由认证层（[com.novamind.app.feature.auth.AuthViewModel]）在登录 / 续期 / 登出 / 游客时维护，
 * 以 [session] 暴露为可观察 [StateFlow]，供其他模块（如日历）**响应式**联动——
 * 会话变化（如游客→登录）时，日历能据此重新评估，而非只在创建时读一次。
 *
 * - [AppSession.userKey] 取登录用户邮箱，用于「登录账户 ↔ 日历绑定」软一致性校验。
 * - [AppSession.isGuest] 为 true（免登录）时不允许使用日历。
 */
object AppUserProvider {

    private val _session = MutableStateFlow(AppSession())
    val session: StateFlow<AppSession> = _session.asStateFlow()

    val currentUserKey: String? get() = _session.value.userKey
    val isGuest: Boolean get() = _session.value.isGuest

    /** 设为真实登录用户（非游客）。 */
    fun setUser(email: String?) {
        _session.value = AppSession(userKey = email, isGuest = false)
    }

    /** 设为游客会话。 */
    fun setGuest() {
        _session.value = AppSession(userKey = null, isGuest = true)
    }

    /** 清空会话（登出 / 退出游客）。 */
    fun clear() {
        _session.value = AppSession()
    }
}
