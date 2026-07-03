package com.novamind.app.common.log

import com.novamind.app.common.config.AppConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用内日志缓冲（Debug 工具箱用）：环形缓冲，UI 可观察、可清空、可导出分享。
 *
 * 已迁移为 [AppLog] 管道的内存视图：写入统一走 AppLog（经 [MemorySink] 回写此处），
 * 本对象只保留观察/导出能力。新代码请直接用 [AppLog]。
 */
object DebugLog {

    enum class Level { V, D, I, W, E }

    data class Entry(val time: Long, val level: Level, val tag: String, val msg: String)

    private val _logs = MutableStateFlow<List<Entry>>(emptyList())
    val logs: StateFlow<List<Entry>> = _logs.asStateFlow()

    // ── 写入：转发 AppLog 管道（保留旧签名，调用方不动）──────────────────────

    fun log(level: Level, tag: String, msg: String) = when (level) {
        Level.V -> AppLog.v(tag) { msg }
        Level.D -> AppLog.d(tag) { msg }
        Level.I -> AppLog.i(tag) { msg }
        Level.W -> AppLog.w(tag) { msg }
        Level.E -> AppLog.e(tag) { msg }
    }

    fun v(tag: String, msg: String) = log(Level.V, tag, msg)
    fun d(tag: String, msg: String) = log(Level.D, tag, msg)
    fun i(tag: String, msg: String) = log(Level.I, tag, msg)
    fun w(tag: String, msg: String) = log(Level.W, tag, msg)
    fun e(tag: String, msg: String) = log(Level.E, tag, msg)

    /** 仅供 [MemorySink]（管道消费端）回写，业务代码勿直接调用。 */
    internal fun append(event: LogEvent) {
        val entry = Entry(event.time, event.level.toDebugLevel(), event.tag, event.msg)
        _logs.value = (_logs.value + entry).takeLast(AppConfig.Log.MEMORY_BUFFER_SIZE)
    }

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

    private fun LogLevel.toDebugLevel(): Level = when (this) {
        LogLevel.V -> Level.V
        LogLevel.D -> Level.D
        LogLevel.I -> Level.I
        LogLevel.W -> Level.W
        LogLevel.E -> Level.E
    }
}
