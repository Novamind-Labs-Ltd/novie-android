package com.novamind.app.data

import com.novamind.app.data.db.RecordingDao
import com.novamind.app.data.db.RecordingEntity
import com.novamind.app.data.db.RecordingSegmentEntity
import com.novamind.app.data.db.RecordingWithSegments
import com.novamind.app.data.db.UploadStatus
import com.novamind.app.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class RoomRecordingRepository(private val dao: RecordingDao) : RecordingRepository {

    override fun recordingsOfNote(noteId: String): Flow<List<RecordingWithSegments>> =
        dao.recordingsOfNote(noteId)

    override suspend fun saveRecording(
        noteId: String,
        recordingId: String,
        segmentPaths: List<String>,
        durationMs: Long,
    ) = withContext(Dispatchers.IO) {
        // 逐分片算哈希与字节数（IO 线程）
        val segments = segmentPaths.mapIndexed { i, path ->
            val file = File(path)
            RecordingSegmentEntity(
                id = UUID.randomUUID().toString(),
                recordingId = recordingId,
                index = i,
                path = path,
                bytes = file.length(),
                durationMs = 0L,                 // 单片时长未知，留 0（整体时长在录音上）
                sha256 = FileUtils.sha256(file),
            )
        }
        val recording = RecordingEntity(
            id = recordingId,
            noteId = noteId,
            createdAt = System.currentTimeMillis(),
            durationMs = durationMs,
            totalBytes = segments.sumOf { it.bytes },
            segmentCount = segments.size,
            sha256 = FileUtils.recordingHash(segments.map { it.sha256 }),
        )
        // 先插录音（满足外键），再插分片
        dao.upsertRecording(recording)
        dao.upsertSegments(segments)
    }

    override suspend fun deleteRecording(recordingId: String) = withContext(Dispatchers.IO) {
        dao.segmentPaths(recordingId).forEach { runCatching { File(it).delete() } }
        dao.deleteRecording(recordingId)
    }

    override suspend fun deleteRecordingFilesOfNote(noteId: String) = withContext(Dispatchers.IO) {
        dao.segmentPathsOfNote(noteId).forEach { runCatching { File(it).delete() } }
    }

    override suspend fun pendingUploads(): List<RecordingWithSegments> = dao.pendingUploads()

    override suspend fun updateSegmentStatus(segmentId: String, status: UploadStatus) =
        dao.setSegmentStatus(segmentId, status.name)

    override suspend fun updateRecordingStatus(
        recordingId: String,
        status: UploadStatus,
        remoteUrl: String?,
    ) = dao.setRecordingStatus(recordingId, status.name, remoteUrl)

    override suspend fun knownSegmentPaths(): Set<String> =
        dao.allSegmentPaths().toSet()
}
