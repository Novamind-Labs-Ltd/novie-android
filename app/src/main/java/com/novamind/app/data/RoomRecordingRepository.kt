package com.novamind.app.data

import com.novamind.app.data.db.RecordingDao
import com.novamind.app.data.db.RecordingEntity
import com.novamind.app.data.db.UploadStatus
import com.novamind.app.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class RoomRecordingRepository(private val dao: RecordingDao) : RecordingRepository {

    override fun recordingsOfNote(noteId: String): Flow<List<RecordingEntity>> =
        dao.recordingsOfNote(noteId)

    override suspend fun saveRecording(
        noteId: String,
        recordingId: String,
        path: String,
        durationMs: Long,
    ) = withContext(Dispatchers.IO) {
        val file = File(path)
        val recording = RecordingEntity(
            id = recordingId,
            noteId = noteId,
            createdAt = System.currentTimeMillis(),
            durationMs = durationMs,
            path = path,
            bytes = file.length(),
            sha256 = FileUtils.sha256(file),
        )
        dao.upsertRecording(recording)
    }

    override suspend fun deleteRecording(recordingId: String) = withContext(Dispatchers.IO) {
        dao.pathOf(recordingId)?.let { runCatching { File(it).delete() } }
        dao.deleteRecording(recordingId)
    }

    override suspend fun deleteRecordingFilesOfNote(noteId: String) = withContext(Dispatchers.IO) {
        dao.pathsOfNote(noteId).forEach { runCatching { File(it).delete() } }
    }

    override suspend fun pendingUploads(): List<RecordingEntity> = dao.pendingUploads()

    override suspend fun reclaimableOldestFirst(): List<RecordingEntity> = dao.reclaimableOldestFirst()

    override suspend fun updateRecordingStatus(
        recordingId: String,
        status: UploadStatus,
        fileId: String?,
        remoteUrl: String?,
    ) = dao.setRecordingStatus(recordingId, status.name, fileId, remoteUrl)

    override suspend fun knownPaths(): Set<String> = dao.allPaths().toSet()
}
