package com.novamind.app.util

import android.content.Context
import com.novamind.app.BuildConfig
import com.novamind.app.common.net.ApiConfig
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryAttribute
import io.sentry.SentryAttributes
import io.sentry.SentryLevel
import io.sentry.SentryLogLevel
import io.sentry.android.core.SentryAndroid
import io.sentry.logger.SentryLogParameters
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
            options.tracesSampleRate = 0.toDouble()
            // 开启 Structured Logs（需 SDK >= 8.12.0），日志进入 Sentry「Logs」视图
            options.logs.isEnabled = false
            // UI Profiling（trace 模式，依赖 tracing；需 SDK >= 8.7.0）：
            // 有采样 span 时自动采集性能剖析，按 session 采样，prod 压低
            options.profileLifecycle = io.sentry.ProfileLifecycle.TRACE
            options.profileSessionSampleRate = 0.toDouble()
        }
    }

    // ── Structured Logs（Sentry.logger）──────────────────────────────────────

    /** 结构化日志：info 级，进入 Sentry Logs 视图（可按 environment 检索）。 */
    fun logInfo(message: String) {
        Sentry.logger().info(message)
    }

    /** 结构化日志：warn 级。 */
    fun logWarn(message: String) {
        Sentry.logger().warn(message)
    }

    /** 结构化日志：error 级。 */
    fun logError(message: String) {
        Sentry.logger().error(message)
    }

    /**
     * 「宽事件」日志：一条日志携带整次操作的完整上下文（推荐写法，便于在 Logs UI 检索过滤）。
     * 属性按值类型自动推断（String/Boolean/整数/浮点等），key 建议统一用 snake_case。
     *
     * 例：`logEvent("Checkout completed", attributes = mapOf("order_id" to id, "cart_value" to 99.9))`
     */
    fun logEvent(
        message: String,
        level: SentryLogLevel = SentryLogLevel.INFO,
        attributes: Map<String, Any?> = emptyMap(),
    ) {
        if (attributes.isEmpty()) {
            Sentry.logger().log(level, message)
            return
        }
        val attrs = SentryAttributes.of(
            *attributes.map { (k, v) -> SentryAttribute.named(k, v) }.toTypedArray()
        )
        Sentry.logger().log(level, SentryLogParameters.create(attrs), message)
    }

    /** 作用域属性：自动附加到后续所有日志（如 request_id、用户分层）；传播至该作用域内。 */
    fun setLogAttribute(key: String, value: Any?) {
        Sentry.setAttribute(key, value)
    }

    /** 移除先前设置的作用域日志属性。 */
    fun removeLogAttribute(key: String) {
        Sentry.removeAttribute(key)
    }

    // ── 应用指标（Sentry.metrics，需 SDK >= 8.34.0）─────────────────────────────

    /** 计数器：累加值（如按钮点击、函数调用次数）。 */
    fun metricCount(name: String, value: Double = 1.0) {
        Sentry.metrics().count(name, value)
    }

    /** 量规：可增可减的瞬时值（如队列长度、内存占用）。 */
    fun metricGauge(name: String, value: Double) {
        Sentry.metrics().gauge(name, value)
    }

    /** 分布：用于统计分布的数值（如响应耗时）。 */
    fun metricDistribution(name: String, value: Double) {
        Sentry.metrics().distribution(name, value)
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
