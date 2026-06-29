package com.novamind.app.util

import android.util.Log
import com.novamind.app.BuildConfig

/**
 * 日志工具：统一封装 [android.util.Log]。
 *
 * - 统一默认 TAG，调用处可覆盖；
 * - verbose / debug / info 仅在 Debug 构建输出，Release 自动静默（[BuildConfig.DEBUG]）；
 * - warn / error 始终输出，并支持携带异常堆栈。
 */
object LogUtils {

    private const val DEFAULT_TAG = "Novie"

    /** 总开关：Debug 构建才输出低级别日志。 */
    private val loggable: Boolean = BuildConfig.DEBUG

    fun v(message: String, tag: String = DEFAULT_TAG) {
        if (loggable) Log.v(tag, message)
    }

    fun d(message: String, tag: String = DEFAULT_TAG) {
        if (loggable) Log.d(tag, message)
    }

    fun i(message: String, tag: String = DEFAULT_TAG) {
        if (loggable) Log.i(tag, message)
    }

    fun w(message: String, throwable: Throwable? = null, tag: String = DEFAULT_TAG) {
        Log.w(tag, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null, tag: String = DEFAULT_TAG) {
        Log.e(tag, message, throwable)
    }
}
