package com.novamind.app.feature.create.recording

import com.novamind.app.common.config.AppConfig
import com.novamind.app.common.session.AppUserProvider
import com.novamind.app.data.FilesRepository
import java.io.File

/**
 * 录音上传器的真实实现：把本地录音文件经 [FilesRepository] 走后端 files 三段式上传，
 * 成功返回后端 `fileId`（后续由笔记侧调 attachments 挂载，见设计文档 §八.9）。
 *
 * - **游客短路**：无 token 无法 presign，直接失败并给出可读文案；登录后可 retryUpload。
 * - 替换状态机默认的 [RecordingUploader.None] 占位。
 *
 * 注：当前为协程内（viewModelScope）上传；跨进程/离屏续传（WorkManager）留待后续增强
 * （见设计文档 §六「可靠性」）。
 */
class FileRecordingUploader(
    private val filesRepository: FilesRepository = FilesRepository(),
) : RecordingUploader {

    override suspend fun upload(file: RecordedFile): Result<String?> {
        if (AppUserProvider.isGuest) {
            return Result.failure(IllegalStateException("游客未登录，录音已暂存本地，登录后可同步"))
        }
        return filesRepository
            .uploadFile(File(file.path), AppConfig.Media.AUDIO_MIME)
            .map { fileId -> fileId } // Result<String> → Result<String?>
    }
}
