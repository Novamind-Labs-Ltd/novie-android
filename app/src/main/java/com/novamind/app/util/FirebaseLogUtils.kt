package com.novamind.app.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Firebase 日志工具：统一封装 Crashlytics（崩溃日志 / 非致命异常上报）与 Analytics（事件埋点）。
 *
 * 调用前 Firebase 须已初始化（应用已集成 google-services，默认在进程启动时自动完成）。
 */
object FirebaseLogUtils {

    private val crashlytics: FirebaseCrashlytics get() = FirebaseCrashlytics.getInstance()

    // ── Crashlytics ───────────────────────────────────────────────────────────

    /** 写一条面包屑日志（随下次崩溃 / 异常一并上报，便于还原现场）。 */
    fun log(message: String) {
        crashlytics.log(message)
    }

    /** 上报一个非致命异常；可附带一条说明日志。 */
    fun recordException(throwable: Throwable, message: String? = null) {
        if (!message.isNullOrBlank()) crashlytics.log(message)
        crashlytics.recordException(throwable)
    }

    /** 关联用户标识（脱敏后的 id），便于按用户排查。 */
    fun setUserId(userId: String) {
        crashlytics.setUserId(userId)
    }

    /** 设置自定义键值，附加到崩溃报告上下文。 */
    fun setCustomKey(key: String, value: String) {
        crashlytics.setCustomKey(key, value)
    }

    /** 开关崩溃数据收集（如尊重用户隐私设置）。 */
    fun setCrashlyticsEnabled(enabled: Boolean) {
        crashlytics.isCrashlyticsCollectionEnabled = enabled
    }

    // ── Analytics ─────────────────────────────────────────────────────────────

    /** 记录一个 Analytics 事件，[params] 会按类型转入 Bundle（其余类型按字符串处理）。 */
    fun logEvent(context: Context, name: String, params: Map<String, Any?> = emptyMap()) {
        val bundle = Bundle().apply {
            params.forEach { (key, value) ->
                when (value) {
                    null -> Unit
                    is String -> putString(key, value)
                    is Boolean -> putLong(key, if (value) 1L else 0L)
                    is Int -> putLong(key, value.toLong())
                    is Long -> putLong(key, value)
                    is Float -> putDouble(key, value.toDouble())
                    is Double -> putDouble(key, value)
                    else -> putString(key, value.toString())
                }
            }
        }
        FirebaseAnalytics.getInstance(context).logEvent(name, bundle)
    }
}
