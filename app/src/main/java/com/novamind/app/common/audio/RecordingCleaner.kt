package com.novamind.app.common.audio

import com.novamind.app.common.log.AppLog
import android.content.Context
import android.os.Looper
import android.os.StatFs
import com.novamind.app.common.config.AppConfig
import com.novamind.app.data.db.AppDatabase
import com.novamind.app.data.db.RoomRecordingRepository
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 录音文件的空间回收（DB 感知，安全删除）：可用空间偏低时，
 * 先清孤儿文件，再按「最久优先」删**已上传/已校验**的录音，直到空间回升到目标值。
 *
 * **绝不**删除 LOCAL_ONLY / PENDING / UPLOADING 的录音（未上传即删会丢数据），
 * 亦通过「文件年龄保护」避免误删正在录制的文件。
 *
 * 阈值集中在 [AppConfig.Media]。
 */
object RecordingCleaner {

    /** 录音存放目录（相对 filesDir），与 [AudioRecorder] 保持一致。 */
    private const val AUDIO_DIR = "note_audio"

    /** 孤儿文件年龄保护：新于此值的未登记文件视为「可能正在录制」，不删。 */
    private const val ORPHAN_MIN_AGE_MS = 10 * 60 * 1000L

    private const val TAG = "RecCleaner"

    /** App 启动空闲时触发一次清理（后台线程执行），不阻塞启动。 */
    fun scheduleOnIdle(context: Context) {
        val appContext = context.applicationContext
        Looper.getMainLooper().queue.addIdleHandler {
            Thread({ runCatching { runBlocking { cleanupIfNeeded(appContext) } } }, "rec-cleaner").start()
            false // 仅执行一次后移除
        }
    }

    /**
     * 录制前确保空间充足：不足则先清理，返回清理后是否达到 [AppConfig.Media.RECORD_MIN_FREE_MB]。
     * 供 [RecordingService] 在开始录音前调用（须在 IO 线程）。
     */
    suspend fun ensureSpaceForRecording(context: Context): Boolean {
        val minBytes = AppConfig.Media.RECORD_MIN_FREE_MB * 1024 * 1024
        if (availableBytes(context) >= minBytes) return true
        cleanupIfNeeded(context)
        return availableBytes(context) >= minBytes
    }

    /**
     * 可用空间 < [AppConfig.Media.STORAGE_MIN_FREE_MB] 时：先清孤儿文件，
     * 再按最久优先删除可回收录音，达到 [AppConfig.Media.STORAGE_TARGET_FREE_MB] 或无可删即停止。
     */
    suspend fun cleanupIfNeeded(context: Context) {
        val minFreeBytes = AppConfig.Media.STORAGE_MIN_FREE_MB * 1024 * 1024
        val targetBytes = AppConfig.Media.STORAGE_TARGET_FREE_MB * 1024 * 1024
        if (availableBytes(context) >= minFreeBytes) return

        val repo = RoomRecordingRepository(AppDatabase.getInstance(context).recordingDao())

        // 1) 孤儿文件：不在库、且非新近（可能正在录制）的残留文件，直接删。
        val known = repo.knownPaths()
        val cutoff = System.currentTimeMillis() - ORPHAN_MIN_AGE_MS
        var orphanDeleted = 0
        File(context.filesDir, AUDIO_DIR).listFiles()?.forEach { f ->
            if (f.isFile && f.absolutePath !in known && f.lastModified() < cutoff) {
                if (f.delete()) orphanDeleted++
            }
        }

        // 2) 已上传/已校验的录音，最旧优先删（连库行 + 磁盘文件），直至达标。
        var recDeleted = 0
        if (availableBytes(context) < targetBytes) {
            for (rec in repo.reclaimableOldestFirst()) {
                if (availableBytes(context) >= targetBytes) break
                repo.deleteRecording(rec.id)
                recDeleted++
            }
        }

        AppLog.i(TAG) { "cleanup done: orphans=$orphanDeleted recordings=$recDeleted " +
                "free=${availableBytes(context) / (1024 * 1024)}MB" }
    }

    /** 内部存储分区当前可用字节；读取失败时返回最大值（视为充足，不触发清理）。 */
    private fun availableBytes(context: Context): Long =
        runCatching { StatFs(context.filesDir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)
}
