package com.novamind.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Upsert
    suspend fun upsertRecording(recording: RecordingEntity)

    @Upsert
    suspend fun upsertSegments(segments: List<RecordingSegmentEntity>)

    /** 某条笔记的全部录音（含分片），按创建时间升序，实时 Flow。 */
    @Transaction
    @Query("SELECT * FROM recordings WHERE noteId = :noteId ORDER BY createdAt")
    fun recordingsOfNote(noteId: String): Flow<List<RecordingWithSegments>>

    /** 尚未整体校验通过（VERIFIED）的录音，用于（续）上传。 */
    @Transaction
    @Query("SELECT * FROM recordings WHERE uploadStatus != 'VERIFIED'")
    suspend fun pendingUploads(): List<RecordingWithSegments>

    @Query("UPDATE recording_segments SET uploadStatus = :status WHERE id = :segmentId")
    suspend fun setSegmentStatus(segmentId: String, status: String)

    @Query("UPDATE recordings SET uploadStatus = :status, remoteUrl = :remoteUrl WHERE id = :recordingId")
    suspend fun setRecordingStatus(recordingId: String, status: String, remoteUrl: String?)

    /** 取某录音全部分片的本地路径（删除前用于清理磁盘文件）。 */
    @Query("SELECT path FROM recording_segments WHERE recordingId = :recordingId")
    suspend fun segmentPaths(recordingId: String): List<String>

    /** 某笔记下全部分片路径（删笔记前清理文件用）。 */
    @Query(
        "SELECT s.path FROM recording_segments s " +
            "INNER JOIN recordings r ON s.recordingId = r.id WHERE r.noteId = :noteId",
    )
    suspend fun segmentPathsOfNote(noteId: String): List<String>

    /** 删除录音行（分片行随外键级联删除；磁盘文件需另行删除）。 */
    @Query("DELETE FROM recordings WHERE id = :recordingId")
    suspend fun deleteRecording(recordingId: String)

    /** 全部录音分片路径（孤儿文件清理用）。 */
    @Query("SELECT path FROM recording_segments")
    suspend fun allSegmentPaths(): List<String>
}
