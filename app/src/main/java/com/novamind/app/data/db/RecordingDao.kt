package com.novamind.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Upsert
    suspend fun upsertRecording(recording: RecordingEntity)

    /** 某条笔记的全部录音，按创建时间升序，实时 Flow。 */
    @Query("SELECT * FROM recordings WHERE noteId = :noteId ORDER BY createdAt")
    fun recordingsOfNote(noteId: String): Flow<List<RecordingEntity>>

    /** 按 id 取单条录音。 */
    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: String): RecordingEntity?

    /** 尚未整体校验通过（VERIFIED）的录音，用于（续）上传。 */
    @Query("SELECT * FROM recordings WHERE uploadStatus != 'VERIFIED'")
    suspend fun pendingUploads(): List<RecordingEntity>

    /** 可安全回收的录音（已上传/已校验），最旧在前，供空间清理。 */
    @Query("SELECT * FROM recordings WHERE uploadStatus IN ('UPLOADED', 'VERIFIED') ORDER BY createdAt")
    suspend fun reclaimableOldestFirst(): List<RecordingEntity>

    @Query("UPDATE recordings SET uploadStatus = :status, fileId = :fileId, remoteUrl = :remoteUrl WHERE id = :recordingId")
    suspend fun setRecordingStatus(recordingId: String, status: String, fileId: String?, remoteUrl: String?)

    /** 取某录音文件路径（删除前用于清理磁盘文件）。 */
    @Query("SELECT path FROM recordings WHERE id = :recordingId")
    suspend fun pathOf(recordingId: String): String?

    /** 某笔记下全部录音文件路径（删笔记前清理文件用）。 */
    @Query("SELECT path FROM recordings WHERE noteId = :noteId")
    suspend fun pathsOfNote(noteId: String): List<String>

    /** 删除录音行（磁盘文件需另行删除）。 */
    @Query("DELETE FROM recordings WHERE id = :recordingId")
    suspend fun deleteRecording(recordingId: String)

    /** 全部录音文件路径（孤儿文件清理用）。 */
    @Query("SELECT path FROM recordings")
    suspend fun allPaths(): List<String>
}
