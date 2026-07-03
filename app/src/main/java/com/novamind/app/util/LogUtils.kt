package com.novamind.app.util

import com.novamind.app.common.log.AppLog

/**
 * 日志工具（遗留门面）：已迁移为转发 [AppLog]，行为对齐迁移前
 * （V/D/I Release 静默、W/E 始终输出），额外获得落盘能力。
 * 新代码请直接用 [AppLog]。
 */
object LogUtils {

    private const val DEFAULT_TAG = "Novie"

    fun v(message: String, tag: String = DEFAULT_TAG) = AppLog.v(tag) { message }

    fun d(message: String, tag: String = DEFAULT_TAG) = AppLog.d(tag) { message }

    fun i(message: String, tag: String = DEFAULT_TAG) = AppLog.i(tag) { message }

    fun w(message: String, throwable: Throwable? = null, tag: String = DEFAULT_TAG) =
        AppLog.w(tag, throwable) { message }

    fun e(message: String, throwable: Throwable? = null, tag: String = DEFAULT_TAG) =
        AppLog.e(tag, throwable) { message }
}
