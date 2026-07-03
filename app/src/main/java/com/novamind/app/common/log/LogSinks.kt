package com.novamind.app.common.log

import android.util.Log
import com.novamind.app.util.SentryUtils
import io.sentry.SentryLevel

/** 日志输出端。所有回调在管道的单消费者协程串行执行，实现无需加锁。 */
internal interface LogSink {
    fun onLog(event: LogEvent)

    /** 定时 / 手动 flush（W/E 级别的即时 flush 由 FileLogSink 自行处理）。 */
    fun onFlush() {}
}

/** Logcat：Debug 全量；Release 只透传 W/E（与迁移前 LogUtils 行为一致）。 */
internal class LogcatSink(private val debuggable: Boolean) : LogSink {

    override fun onLog(event: LogEvent) {
        if (!debuggable && event.level < LogLevel.W) return
        when (event.level) {
            LogLevel.V -> Log.v(event.tag, event.msg)
            LogLevel.D -> Log.d(event.tag, event.msg)
            LogLevel.I -> Log.i(event.tag, event.msg)
            LogLevel.W -> Log.w(event.tag, event.msg, event.throwable)
            LogLevel.E -> Log.e(event.tag, event.msg, event.throwable)
        }
    }
}

/** 内存环形缓冲：回写 [DebugLog]，供 Debug 工具箱观察 / 导出。 */
internal class MemorySink : LogSink {

    override fun onLog(event: LogEvent) {
        DebugLog.append(event)
    }
}

/**
 * 云端转发（CLOUD 档专用）：
 * - E + 异常 → Sentry capture（含堆栈）；
 * - 其余 → Sentry captureMessage；
 * 另外 W/E 无论档位都补一条 breadcrumb（本地暂存，随后续事件上传，成本低）。
 */
internal class CloudRelaySink : LogSink {

    override fun onLog(event: LogEvent) {
        if (event.level >= LogLevel.W) {
            SentryUtils.breadcrumb(
                message = "[${event.tag}] ${event.msg}",
                category = "applog",
                level = if (event.level == LogLevel.E) SentryLevel.ERROR else SentryLevel.WARNING,
            )
        }
        if (event.policy != Policy.CLOUD) return

        val message = "[${event.tag}] ${event.msg}"
        if (event.level == LogLevel.E && event.throwable != null) {
            SentryUtils.capture(event.throwable, message)
        } else {
            SentryUtils.log(message, event.level.toSentry())
        }
    }

    private fun LogLevel.toSentry(): SentryLevel = when (this) {
        LogLevel.V, LogLevel.D -> SentryLevel.DEBUG
        LogLevel.I -> SentryLevel.INFO
        LogLevel.W -> SentryLevel.WARNING
        LogLevel.E -> SentryLevel.ERROR
    }
}
