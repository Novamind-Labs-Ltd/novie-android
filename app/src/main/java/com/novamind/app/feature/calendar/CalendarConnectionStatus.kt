package com.novamind.app.feature.calendar

/**
 * 日历连接状态——页面唯一渲染依据（single source of truth）。
 *
 * 设计见同目录 calendar-connection-design.md。要点：
 * - access token ~1h 过期且无 refresh token，"保持登录"靠系统侧记住的授权静默续期；
 * - [TOKEN_EXPIRED] 多为瞬态（静默续期成功即回 [SYNCING]）；
 * - [PERMISSION_REVOKED] 与 [NOT_CONNECTED] 区别在「是否仍有本地绑定记录」。
 */
enum class CalendarConnectionStatus {
    /** 从未连接，或已断开。显示首次连接引导卡片。 */
    NOT_CONNECTED,

    /** 已授权，正在拉取事件（有缓存时仍展示旧列表 + 刷新指示）。 */
    SYNCING,

    /** 已授权且 token 有效，事件就绪。 */
    CONNECTED,

    /** token 过期，可静默续期（通常瞬态）。 */
    TOKEN_EXPIRED,

    /** 授权被撤销（Google 账号设置里取消 / revoke 后），需重新走同意流程。 */
    PERMISSION_REVOKED,

    /** 非授权类失败（网络 / 服务端），提供重试。 */
    SYNC_FAILED,
}
