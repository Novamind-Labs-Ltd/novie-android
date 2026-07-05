package com.novamind.app.common.audio

import com.novamind.app.common.log.AppLog
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.novamind.app.common.config.AppConfig
import com.novamind.app.data.FilesRepository
import com.novamind.app.data.RecordingRepository
import com.novamind.app.data.db.UploadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * 录音上传后台任务（WorkManager + Hilt）：把一条已落库的录音走 files 三段式上传，
 * 并把结果回写 [RecordingRepository]（持久真相以 Room `uploadStatus` 为准）。
 *
 * 相比在 viewModelScope 里上传，Worker 可**跨界面/跨进程续传**、按网络约束调度、失败指数退避重试。
 * 由 [RecordingUploadScheduler] 入队（携带 recordingId）。
 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val filesRepository: FilesRepository,
    private val recordingRepository: RecordingRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_RECORDING_ID) ?: return Result.failure()
        val rec = recordingRepository.getRecording(id) ?: return Result.success() // 已删/已处理
        // 已上传/已校验：幂等直接成功。
        if (rec.uploadStatus == UploadStatus.UPLOADED.name || rec.uploadStatus == UploadStatus.VERIFIED.name) {
            return Result.success()
        }

        recordingRepository.updateRecordingStatus(id, UploadStatus.UPLOADING)
        val outcome = filesRepository.uploadFile(File(rec.path), AppConfig.Media.AUDIO_MIME)
        return outcome.fold(
            onSuccess = { fileId ->
                recordingRepository.updateRecordingStatus(id, UploadStatus.UPLOADED, fileId = fileId)
                AppLog.i(TAG) { "upload ok id=$id fileId=$fileId" }
                Result.success()
            },
            onFailure = { e ->
                AppLog.w(TAG) { "upload failed id=$id: ${e.message}" }
                recordingRepository.updateRecordingStatus(id, UploadStatus.FAILED)
                if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure()
            },
        )
    }

    companion object {
        const val KEY_RECORDING_ID = "recordingId"
        private const val TAG = "UploadWorker"
        private const val MAX_ATTEMPTS = 5
    }
}
