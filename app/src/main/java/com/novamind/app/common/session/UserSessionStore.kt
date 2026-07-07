package com.novamind.app.common.session

import com.novamind.app.common.storage.MmkvStore

/**
 * 用户会话持久化（MMKV，仿 [com.novamind.app.common.profile.ProfileStore]）。
 *
 * 缓存后端档案 `userId/displayName/email/avatarUrl/registered`，供冷启动即有、离线兜底显示；
 * **不存 token**（token 仍走 [com.novamind.app.common.net.TokenProvider] / 安全存储）。
 *
 * 前置：Application.onCreate 已调 `MMKV.initialize(context)`（见 [MmkvStore]）。
 */
class UserSessionStore {

    private val store by lazy { MmkvStore(PREF) }

    /** 读缓存档案；无 userId（从未落盘/已清）返回 null。 */
    fun load(): CachedProfile? {
        val userId = store.getString(KEY_USER_ID, null) ?: return null
        return CachedProfile(
            userId = userId,
            displayName = store.getString(KEY_DISPLAY_NAME, null),
            email = store.getString(KEY_EMAIL, null),
            avatarUrl = store.getString(KEY_AVATAR_URL, null),
            registered = store.getBoolean(KEY_REGISTERED, false),
        )
    }

    /** 写入档案 + registered。profile 为 null 时仅更新 registered（如已登录未建档）。 */
    fun save(profile: UserProfile?, registered: Boolean) {
        if (profile != null) {
            store.putString(KEY_USER_ID, profile.userId)
            store.putString(KEY_DISPLAY_NAME, profile.displayName)
            store.putString(KEY_EMAIL, profile.email)
            store.putString(KEY_AVATAR_URL, profile.avatarUrl)
        }
        store.putBoolean(KEY_REGISTERED, registered)
    }

    /** 清空缓存（登出 / 账户切换，避免串号）。 */
    fun clear() = store.clear()

    /** MMKV 缓存的档案快照。 */
    data class CachedProfile(
        val userId: String,
        val displayName: String?,
        val email: String?,
        val avatarUrl: String?,
        val registered: Boolean,
    ) {
        fun toProfile() = UserProfile(userId, displayName, email, avatarUrl)
    }

    private companion object {
        const val PREF = "user_session"
        const val KEY_USER_ID = "user_id"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_EMAIL = "email"
        const val KEY_AVATAR_URL = "avatar_url"
        const val KEY_REGISTERED = "registered"
    }
}
