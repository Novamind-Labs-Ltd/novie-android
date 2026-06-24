package com.novamind.app.common.audio

import android.content.Context
import android.os.Looper
import android.os.StatFs
import com.novamind.app.common.log.DebugLog
import java.io.File

/**
 * 录音文件的空间回收：当内部存储可用空间偏低时，按「最久优先」删除应用内录音，
 * 直到可用空间回升到目标值。在 App 启动后的空闲时机触发一次，不阻塞启动。
 *
 * 阈值集中在此，便于随时调整。
 */
object RecordingCleaner {

    /** 可用空间低于此值（MB）时触发清理。 */
    const val MIN_FREE_MB = 30L

    /** 清理到可用空间达到此值（MB）即停止。 */
    const val TARGET_FREE_MB = 200L

    /** 录音存放目录（相对 filesDir），与 [AudioRecorder] 保持一致。 */
    private const val AUDIO_DIR = "note_audio"

    private const val TAG = "RecCleaner"

    /**
     * 在主线程空闲时触发一次清理（实际 IO 在后台线程执行），适合 App 启动调用。
     */
    fun scheduleOnIdle(context: Context) {
        val appContext = context.applicationContext
        Looper.getMainLooper().queue.addIdleHandler {
            Thread({ runCatching { cleanupIfNeeded(appContext) } }, "rec-cleaner").start()
            false // 仅执行一次后移除
        }
    }

    /**
     * 可用空间 < [MIN_FREE_MB] 时，按最后修改时间从旧到新删除录音，
     * 每删一个就重新检查，达到 [TARGET_FREE_MB] 或无文件可删即停止。
     */
    fun cleanupIfNeeded(context: Context) {
        val minFreeBytes = MIN_FREE_MB * 1024 * 1024
        val targetBytes = TARGET_FREE_MB * 1024 * 1024

        if (availableBytes(context) >= minFreeBytes) return

        val files = File(context.filesDir, AUDIO_DIR).listFiles()?.filter { it.isFile }.orEmpty()
        if (files.isEmpty()) {
            DebugLog.w(TAG, "low space but no recordings to delete")
            return
        }

        // 最久的在前（lastModified 升序）→ 优先删除最久的
        val oldestFirst = files.sortedBy { it.lastModified() }
        var deleted = 0
        for (f in oldestFirst) {
            if (availableBytes(context) >= targetBytes) break
            if (f.delete()) deleted++
        }
        DebugLog.i(
            TAG,
            "cleanup done: deleted=$deleted, free=${availableBytes(context) / (1024 * 1024)}MB " +
                "(min=$MIN_FREE_MB target=$TARGET_FREE_MB)",
        )
    }

    /** 内部存储分区当前可用字节；读取失败时返回最大值（视为充足，不触发清理）。 */
    private fun availableBytes(context: Context): Long =
        runCatching { StatFs(context.filesDir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)
}
