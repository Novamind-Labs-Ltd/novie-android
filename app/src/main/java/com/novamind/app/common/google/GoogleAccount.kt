package com.novamind.app.common.google

/**
 * 已绑定的 Google 账号标识。
 *
 * [email] 取自主日历 id（Calendar API 中 `calendars/primary` 的 id 即用户邮箱），
 * 同时用作事件缓存的分区 key，保证「切换账号不复用旧数据」。
 */
data class GoogleAccount(
    val email: String,
    val displayName: String? = null,
)
