package com.novamind.app.common.log

import android.content.Context
import com.novamind.app.BuildConfig
import com.novamind.app.common.config.AppConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 统一日志门面（设计见 log-system-design.md）。
 *
 * 调用方 → 级别过滤 → 脱敏 → Channel → 单消费者协程 → 各 Sink
 * （Logcat / 内存 / 文件 / Sentry 转发）。消息惰性求值：级别被过滤时
 * lambda 不执行，Release 包 V/D 日志零成本。
 *
 * 在 `Application.onCreate` 最先调用 [init]（不依赖其他初始化）。
 */
object AppLog {

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private sealed interface Msg {
        data class Write(val event: LogEvent) : Msg
        data class Flush(val ack: CompletableDeferred<Unit>? = null) : Msg
    }

    private val channel = Channel<Msg>(AppConfig.Log.CHANNEL_CAPACITY)
    private var sinks: List<LogSink> = emptyList()
    private var fileSink: FileLogSink? = null
    private lateinit var logDir: File

    // ── 初始化 ────────────────────────────────────────────────────────────────

    fun init(context: Context) {
        if (!started.compareAndSet(false, true)) return
        logDir = File(context.applicationContext.filesDir, "logs")

        val file = FileLogSink(logDir)
        fileSink = file
        sinks = listOf(LogcatSink(BuildConfig.DEBUG), MemorySink(), file, CloudRelaySink())

        // 单消费者：串行分发给所有 Sink，Sink 实现免锁
        scope.launch {
            for (msg in channel) {
                when (msg) {
                    is Msg.Write -> sinks.forEach { sink ->
                        runCatching { sink.onLog(msg.event) }
                    }
                    is Msg.Flush -> {
                        sinks.forEach { sink -> runCatching { sink.onFlush() } }
                        msg.ack?.complete(Unit)
                    }
                }
            }
        }
        // 定时 flush：兜底缓冲中未落盘的低级别日志
        scope.launch {
            while (isActive) {
                delay(AppConfig.Log.FLUSH_INTERVAL_MS)
                channel.trySend(Msg.Flush())
            }
        }
        // 过期清理：启动后空闲时执行，不阻塞启动
        scope.launch {
            delay(CLEANUP_DELAY_MS)
            file.cleanup()
        }
        installCrashFlush()
    }

    // ── 记录 API（详见设计文档 §4）────────────────────────────────────────────

    fun v(tag: String, policy: Policy = Policy.TRANSIENT, msg: () -> String) =
        dispatch(LogLevel.V, tag, null, policy, msg)

    fun d(tag: String, policy: Policy = Policy.TRANSIENT, msg: () -> String) =
        dispatch(LogLevel.D, tag, null, policy, msg)

    fun i(tag: String, policy: Policy = Policy.LOCAL, msg: () -> String) =
        dispatch(LogLevel.I, tag, null, policy, msg)

    fun w(tag: String, tr: Throwable? = null, policy: Policy = Policy.LOCAL, msg: () -> String) =
        dispatch(LogLevel.W, tag, tr, policy, msg)

    fun e(tag: String, tr: Throwable? = null, policy: Policy = Policy.CLOUD, msg: () -> String) =
        dispatch(LogLevel.E, tag, tr, policy, msg)

    /** 结构化事件：一条日志携带完整上下文，key=value 形式拼入消息。 */
    fun event(
        tag: String,
        name: String,
        attrs: Map<String, Any?> = emptyMap(),
        policy: Policy = Policy.CLOUD,
    ) = dispatch(LogLevel.I, tag, null, policy) {
        if (attrs.isEmpty()) name
        else "$name ${attrs.entries.joinToString(" ") { (k, v) -> "$k=$v" }}"
    }

    /** flush 全部缓冲并返回日志目录（用户反馈打包上传前调用）。 */
    suspend fun flushAndGetLogDir(): File {
        val ack = CompletableDeferred<Unit>()
        channel.send(Msg.Flush(ack))
        ack.await()
        return logDir
    }

    // ── 内部 ──────────────────────────────────────────────────────────────────

    private fun dispatch(
        level: LogLevel,
        tag: String,
        tr: Throwable?,
        policy: Policy,
        msg: () -> String,
    ) {
        if (!started.get()) return
        // Release 丢弃 V/D：在构造消息前拦截，lambda 不执行
        if (!BuildConfig.DEBUG && level < LogLevel.I) return

        val event = LogEvent(
            time = System.currentTimeMillis(),
            level = level,
            tag = tag,
            msg = PiiMasker.mask(msg()),
            threadName = Thread.currentThread().name,
            policy = policy,
            throwable = tr,
        )
        // 队列满（异常刷屏等极端情况）：丢弃本条，不阻塞调用线程
        channel.trySend(Msg.Write(event))
    }

    /**
     * 崩溃兜底：同步写入崩溃日志并 flush，再交还前一个 handler。
     * AppLog 先于 Sentry 初始化，Sentry 的 handler 会包裹本 handler——
     * 双方都链式调用 prev，崩溃时 Sentry 上报与本地落盘两不误。
     */
    private fun installCrashFlush() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                fileSink?.writeSync(
                    LogEvent(
                        time = System.currentTimeMillis(),
                        level = LogLevel.E,
                        tag = "Crash",
                        msg = "uncaught exception on ${thread.name}",
                        threadName = thread.name,
                        policy = Policy.LOCAL, // 上云由 Sentry 的原 handler 负责，避免重复
                        throwable = throwable,
                    ),
                )
                fileSink?.onFlush()
            }
            prev?.uncaughtException(thread, throwable)
        }
    }

    private const val CLEANUP_DELAY_MS = 10_000L
}
