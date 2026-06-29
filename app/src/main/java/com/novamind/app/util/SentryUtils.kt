package com.novamind.app.util

import android.content.Context
import com.novamind.app.BuildConfig
import com.novamind.app.common.net.ApiConfig
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.User

/**
 * Sentry 工具：按环境初始化 + 统一上报日志、异常与上下文。
 *
 * 关闭 Manifest 自动初始化（`io.sentry.auto-init=false`），改为在 [init] 里按当前环境配置：
 * 各环境（dev / st / prod）均上传，以 `environment` 标签区分；prod 采样率更低。
 * dev 环境可在 Debug 面板手动触发测试上报。
 *
 * 在 `Application.onCreate` 里、`ApiConfig.init` 之后调用 [init]。
 */
object SentryUtils {

    /** 当前环境名（用作 Sentry environment 标签）。 */
    fun environment(): String = when {
        BuildConfig.DEBUG -> "dev"
        ApiConfig.env == ApiConfig.Env.CO -> "prod"
        else -> "st" // CO_NZ
    }

    /**
     * 初始化 Sentry：注入 environment / release 与按环境的采样率。
     * DSN 仍从 Manifest meta-data 读取，这里只做环境相关覆盖。
     */
    fun init(context: Context) {
        val env = environment()
        SentryAndroid.init(context) { options ->
            options.environment = env
            options.release = BuildConfig.VERSION_NAME
            // prod 采样率压低，dev / st 全量便于联调
            options.tracesSampleRate = if (env == "prod") 0.2 else 1.0
        }
    }

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
