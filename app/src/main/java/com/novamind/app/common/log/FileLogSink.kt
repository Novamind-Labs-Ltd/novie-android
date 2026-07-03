package com.novamind.app.common.log

import android.os.StatFs
import com.novamind.app.common.config.AppConfig
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件落盘（LOCAL / CLOUD 档）：按天分片、2MB 滚动、单日限额、7 天保留、低磁盘停写。
 * 参数见 [AppConfig.Log]，设计见 log-system-design.md §5。
 *
 * 正常路径由管道单消费者协程串行调用；崩溃钩子会从崩溃线程直接调 [writeSync]，
 * 故所有文件操作用 [lock] 互斥。
 */
internal class FileLogSink(private val dir: File) : LogSink {

    private val lock = Any()
    private var writer: BufferedWriter? = null
    private var currentFile: File? = null
    private var currentDay = ""
    private var shardIndex = 0

    // 仅在 lock 内使用，无并发问题
    private val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val lineFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    override fun onLog(event: LogEvent) {
        if (event.policy == Policy.TRANSIENT) return
        writeSync(event)
    }

    override fun onFlush() {
        synchronized(lock) { writer?.flush() }
    }

    /** 同步写入；W/E 立即 flush，保证崩溃前关键日志落盘。崩溃钩子也直接走这里。 */
    fun writeSync(event: LogEvent) {
        synchronized(lock) {
            val w = ensureWriter(event.time) ?: return
            runCatching {
                w.write(format(event))
                w.newLine()
                if (event.level >= LogLevel.W) w.flush()
            }.onFailure { writer = null } // 写失败（磁盘满等）：丢弃本条，下次重建
        }
    }

    /** 删除超过保留期的日志文件。启动后空闲时调用。 */
    fun cleanup() {
        synchronized(lock) {
            val deadline = System.currentTimeMillis() -
                AppConfig.Log.RETENTION_DAYS * 86_400_000L
            dir.listFiles { f -> f.isFile && f.name.endsWith(".log") }
                ?.filter { it.lastModified() < deadline }
                ?.forEach { it.delete() }
        }
    }

    // ── 内部 ──────────────────────────────────────────────────────────────────

    private fun format(event: LogEvent): String {
        val base = "${lineFmt.format(Date(event.time))} ${event.level}/${event.tag} " +
            "[${event.threadName}] ${event.msg}"
        val stack = event.throwable?.stackTraceToString()
        return if (stack == null) base else "$base\n$stack"
    }

    /** 确保当前 writer 可用：跨天换文件、超限滚动、磁盘保护。返回 null 表示本条跳过。 */
    private fun ensureWriter(now: Long): BufferedWriter? {
        val day = dayFmt.format(Date(now))

        if (writer == null || day != currentDay) {
            openShard(day, nextShardIndex(day))
        } else if ((currentFile?.length() ?: 0) >= AppConfig.Log.MAX_FILE_BYTES) {
            enforceDayQuota(day)
            openShard(day, shardIndex + 1)
        }
        return writer
    }

    private fun openShard(day: String, index: Int) {
        runCatching {
            writer?.close()
            writer = null
            if (!dir.exists()) dir.mkdirs()
            if (diskLow()) return // 低磁盘：保持 writer 为 null，静默停写

            currentDay = day
            shardIndex = index
            currentFile = File(dir, "novie-$day-$index.log")
            writer = BufferedWriter(FileWriter(currentFile, true), BUFFER_SIZE)
        }.onFailure { writer = null }
    }

    /** 已有分片则接着最后一片续写。 */
    private fun nextShardIndex(day: String): Int =
        shardsOf(day).maxOfOrNull { shardNumber(it) } ?: 0

    /** 单日总量超限时删最旧分片。 */
    private fun enforceDayQuota(day: String) {
        val shards = shardsOf(day).sortedBy { shardNumber(it) }
        var total = shards.sumOf { it.length() }
        for (f in shards) {
            if (total < AppConfig.Log.MAX_DAY_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    private fun shardsOf(day: String): List<File> =
        dir.listFiles { f -> f.name.startsWith("novie-$day-") && f.name.endsWith(".log") }
            ?.toList() ?: emptyList()

    private fun shardNumber(f: File): Int =
        f.name.removeSuffix(".log").substringAfterLast('-').toIntOrNull() ?: 0

    private fun diskLow(): Boolean = runCatching {
        val stat = StatFs(dir.absolutePath)
        stat.availableBytes / (1024 * 1024) < AppConfig.Log.STORAGE_MIN_FREE_MB
    }.getOrDefault(false)

    private companion object {
        const val BUFFER_SIZE = 8 * 1024
    }
}
