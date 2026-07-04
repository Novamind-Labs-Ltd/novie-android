package com.novamind.app.data

import com.novamind.app.data.db.RecordingEntity
import com.novamind.app.data.db.UploadStatus
import kotlinx.coroutines.flow.Flow

/**
 * 录音仓库（单文件，不分片）：录音落库、按笔记查询、删除时清理磁盘文件，并为上传流程读写状态。
 */
interface RecordingRepository {

    /** 某条笔记的全部录音，实时 Flow。 */
    fun recordingsOfNote(noteId: String): Flow<List<RecordingEntity>>

    /**
     * 保存一次录音：算文件 SHA-256 与字节数，连同元信息入库。
     * @param recordingId 建议复用 AudioRecorder 的 sessionId
     * @param path 录音文件绝对路径
     */
    suspend fun saveRecording(
        noteId: String,
        recordingId: String,
        path: String,
        durationMs: Long,
    )

    /** 删除单条录音：先删磁盘文件，再删数据库行。 */
    suspend fun deleteRecording(recordingId: String)

    /** 删除某笔记的全部录音磁盘文件（在删除笔记行之前调用，行由外键级联处理）。 */
    suspend fun deleteRecordingFilesOfNote(noteId: String)

    /** 尚未整体校验通过的录音，用于（续）上传。 */
    suspend fun pendingUploads(): List<RecordingEntity>

    /** 可安全回收（已上传/已校验）的录音，最旧在前，供空间清理。 */
    suspend fun reclaimableOldestFirst(): List<RecordingEntity>

    suspend fun updateRecordingStatus(
        recordingId: String,
        status: UploadStatus,
        fileId: String? = null,
        remoteUrl: String? = null,
    )

    /** 数据库里登记的全部录音文件路径（孤儿文件清理用）。 */
    suspend fun knownPaths(): Set<String>
}
