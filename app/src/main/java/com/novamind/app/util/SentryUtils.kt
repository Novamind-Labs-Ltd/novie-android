package com.novamind.app.util

import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.protocol.User

/**
 * Sentry 工具：统一上报日志、异常与上下文。
 *
 * SDK 由 Sentry Gradle 插件接入、Manifest 中配置 DSN 后自动初始化，直接调用即可。
 */
object SentryUtils {

    /** 上报一条文本日志（默认 INFO 级）。 */
    fun log(message: String, level: SentryLevel = SentryLevel.INFO) {
        Sentry.captureMessage(message, level)
    }

    /** 上报一个异常；可附带一条说明（作为面包屑随异常上传）。 */
    fun capture(throwable: Throwable, message: String? = null) {
        if (!message.isNullOrBlank()) breadcrumb(message)
        Sentry.captureException(throwable)
    }

    /** 添加面包屑（操作轨迹），随后续上报一并展示，便于还原现场。 */
    fun breadcrumb(message: String, category: String? = null, level: SentryLevel = SentryLevel.INFO) {
        val crumb = Breadcrumb().apply {
            this.message = message
            this.level = level
            category?.let { this.category = it }
        }
        Sentry.addBreadcrumb(crumb)
    }

    /** 关联用户标识（脱敏后的 id），便于按用户排查；传 null 清除。 */
    fun setUser(userId: String?) {
        if (userId.isNullOrBlank()) {
            Sentry.setUser(null)
        } else {
            Sentry.setUser(User().apply { id = userId })
        }
    }

    /** 设置全局标签，附加到后续所有事件。 */
    fun setTag(key: String, value: String) {
        Sentry.setTag(key, value)
    }
}
