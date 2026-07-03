package com.novamind.app.common.log

/** 日志级别，按严重度升序（可用 ordinal 比较）。 */
enum class LogLevel { V, D, I, W, E }

/**
 * 输出档位，逐级叠加：TRANSIENT ⊂ LOCAL ⊂ CLOUD（上云必落盘，落盘必出 Logcat）。
 * 与级别正交：级别管过滤，档位管去向。详见 log-system-design.md §4.1。
 */
enum class Policy {
    /** 不落盘：仅 Logcat + 内存缓冲（调试、高频噪音、敏感内容）。 */
    TRANSIENT,

    /** 落盘到本地（默认档）：业务流水，供事后按需拉取。 */
    LOCAL,

    /** 上云：落盘 + 实时上报 Sentry（关键业务事件、需实时告警的错误）。 */
    CLOUD,
}

/** 管道内流转的一条日志。 */
data class LogEvent(
    val time: Long,
    val level: LogLevel,
    val tag: String,
    val msg: String,
    val threadName: String,
    val policy: Policy,
    val throwable: Throwable? = null,
)
