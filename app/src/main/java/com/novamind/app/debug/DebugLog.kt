package com.novamind.app.debug

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用内日志缓冲（Debug 工具箱用）：环形缓冲，UI 可观察、可清空、可导出分享。
 * 同时转发到 Logcat。仅用于 Debug 包。
 */
object DebugLog {

    enum class Level { V, D, I, W, E }

    data class Entry(val time: Long, val level: Level, val tag: String, val msg: String)

    private const val MAX = 500
    private val _logs = MutableStateFlow<List<Entry>>(emptyList())
    val logs: StateFlow<List<Entry>> = _logs.asStateFlow()

    fun log(level: Level, tag: String, msg: String) {
        _logs.value = (_logs.value + Entry(System.currentTimeMillis(), level, tag, msg)).takeLast(MAX)
        when (level) {
            Level.E -> Log.e(tag, msg)
            Level.W -> Log.w(tag, msg)
            Level.I -> Log.i(tag, msg)
            Level.V -> Log.v(tag, msg)
            else -> Log.d(tag, msg)
        }
    }

    fun v(tag: String, msg: String) = log(Level.V, tag, msg)
    fun d(tag: String, msg: String) = log(Level.D, tag, msg)
    fun i(tag: String, msg: String) = log(Level.I, tag, msg)
    fun w(tag: String, msg: String) = log(Level.W, tag, msg)
    fun e(tag: String, msg: String) = log(Level.E, tag, msg)

    fun clear() {
        _logs.value = emptyList()
    }

    /** 导出为可分享的纯文本 */
    fun dump(): String {
        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return _logs.value.joinToString("\n") {
            "${fmt.format(Date(it.time))} ${it.level}/${it.tag}: ${it.msg}"
        }
    }
}
