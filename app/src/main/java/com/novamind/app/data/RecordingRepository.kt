package com.novamind.app.data

import com.novamind.app.data.db.RecordingWithSegments
import com.novamind.app.data.db.UploadStatus
import kotlinx.coroutines.flow.Flow

/**
 * 录音 / 分片仓库：负责把分片落库（含两级 SHA-256 完整性）、按笔记查询、
 * 删除时清理磁盘文件，并为上传流程提供状态读写。
 */
interface RecordingRepository {

    /** 某条笔记的全部录音（含分片），实时 Flow。 */
    fun recordingsOfNote(noteId: String): Flow<List<RecordingWithSegments>>

    /**
     * 保存一次录音：对每个分片算 SHA-256，再算录音整体哈希，连同元信息入库。
     * @param recordingId 建议复用 AudioRecorder 的 sessionId
     * @param segmentPaths 分片文件路径，按录制顺序
     */
    suspend fun saveRecording(
        noteId: String,
        recordingId: String,
        segmentPaths: List<String>,
        durationMs: Long,
    )

    /** 删除单条录音：先删磁盘分片文件，再删数据库行（分片行随外键级联删除）。 */
    suspend fun deleteRecording(recordingId: String)

    /** 删除某笔记的全部录音磁盘文件（在删除笔记行之前调用，行由外键级联处理）。 */
    suspend fun deleteRecordingFilesOfNote(noteId: String)

    /** 尚未整体校验通过的录音，用于（续）上传。 */
    suspend fun pendingUploads(): List<RecordingWithSegments>

    suspend fun updateSegmentStatus(segmentId: String, status: UploadStatus)

    suspend fun updateRecordingStatus(
        recordingId: String,
        status: UploadStatus,
        remoteUrl: String? = null,
    )

    /** 数据库里登记的全部分片路径（孤儿文件清理用）。 */
    suspend fun knownSegmentPaths(): Set<String>
}
