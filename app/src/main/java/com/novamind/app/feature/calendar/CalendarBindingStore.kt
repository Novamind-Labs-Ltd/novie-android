package com.novamind.app.feature.calendar

import com.novamind.app.common.storage.KeyValueStore
import com.novamind.app.common.storage.MmkvStore

/**
 * 日历「绑定」持久化：记录是否曾连接成功、以及绑定的 Google 账号邮箱。
 *
 * 与 token（内存 [com.novamind.app.common.google.GoogleTokenProvider]）和事件缓存
 * （[CalendarEventCache]）三层分离。绑定存在 → 冷启动时尝试静默续期；
 * 绑定不存在 → 显示首次连接引导。
 *
 * 销毁语义见设计文档：退出登录 / 断开 Calendar / 换账号 都会 [clear]；
 * 但只有「换账号」会额外 revoke Google 授权。
 */
class CalendarBindingStore(
    private val store: KeyValueStore = MmkvStore(MMAP_ID),
) {
    /** 是否曾成功连接（决定冷启动走静默续期还是引导卡片）。 */
    val isConnected: Boolean
        get() = store.getBoolean(KEY_CONNECTED, false)

    /** 绑定的 Google 账号邮箱（兼作缓存分区 key）。 */
    val accountEmail: String?
        get() = store.getString(KEY_EMAIL, null)

    /**
     * 绑定时的 App 登录用户 key（邮箱）。用于「登录账户 ↔ 日历」软一致性校验：
     * 进入页面时若与当前登录用户不一致，则清除日历（见设计文档）。游客可为 null。
     */
    val appUserKey: String?
        get() = store.getString(KEY_APP_USER, null)

    /** 首次连接成功后写入绑定：Google 账号邮箱 + 当时的 App 登录用户。 */
    fun bind(email: String, appUserKey: String?) {
        store.putString(KEY_EMAIL, email)
        store.putString(KEY_APP_USER, appUserKey)
        store.putBoolean(KEY_CONNECTED, true)
    }

    /** 清空绑定（退出登录 / 断开 / 换账号前调用）。 */
    fun clear() {
        store.clear()
    }

    private companion object {
        const val MMAP_ID = "calendar_binding"
        const val KEY_CONNECTED = "connected"
        const val KEY_EMAIL = "account_email"
        const val KEY_APP_USER = "app_user_key"
    }
}
