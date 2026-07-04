package com.novamind.app.common.audio

import android.content.Context
import android.os.Looper
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.novamind.app.data.RoomRecordingRepository
import com.novamind.app.data.db.AppDatabase
import com.novamind.app.data.db.UploadStatus
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/**
 * 录音上传调度：把 [UploadWorker] 按 recordingId 入队（网络约束 + 指数退避），
 * 并在 App 启动空闲时把「未完成上传」的录音重新入队，实现断点续传。
 *
 * 唯一任务名 `rec-upload-<id>` + [ExistingWorkPolicy.KEEP] 保证同一录音不会重复入队。
 */
object RecordingUploadScheduler {

    private const val WORK_PREFIX = "rec-upload-"

    /** 入队一条录音的上传任务（录音须已落库）。 */
    fun enqueue(context: Context, recordingId: String) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(UploadWorker.KEY_RECORDING_ID to recordingId))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK_PREFIX + recordingId, ExistingWorkPolicy.KEEP, request)
    }

    /** App 启动空闲时，把待上传（LOCAL_ONLY/PENDING/FAILED/UPLOADING）的录音重新入队。 */
    fun resumeOnIdle(context: Context) {
        val app = context.applicationContext
        Looper.getMainLooper().queue.addIdleHandler {
            Thread({
                runCatching {
                    runBlocking {
                        val repo = RoomRecordingRepository(AppDatabase.getInstance(app).recordingDao())
                        repo.pendingUploads()
                            .filter { it.uploadStatus != UploadStatus.UPLOADED.name }
                            .forEach { enqueue(app, it.id) }
                    }
                }
            }, "rec-upload-resume").start()
            false // 仅执行一次后移除
        }
    }
}
