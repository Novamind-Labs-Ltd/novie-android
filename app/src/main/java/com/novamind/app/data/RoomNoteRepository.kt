package com.novamind.app.data

import com.novamind.app.data.db.NoteDao
import com.novamind.app.data.db.SyncStatus
import com.novamind.app.data.db.toEntity
import com.novamind.app.data.db.toNote
import com.novamind.app.feature.create.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomNoteRepository(private val dao: NoteDao) : NoteRepository {

    override val notes: Flow<List<Note>> =
        dao.getAllNotes().map { entities -> entities.map { it.toNote() } }

    override val deletedNotes: Flow<List<Note>> =
        dao.getDeletedNotes().map { entities -> entities.map { it.toNote() } }

    override suspend fun addOrUpdate(note: Note) {
        // 保留已有的同步元数据（serverId/rev/lastSyncedAt），仅把状态置为「有未同步改动」
        val existing = dao.getById(note.id)
        dao.upsert(
            note.toEntity().copy(
                serverId = existing?.serverId,
                rev = existing?.rev ?: 0L,
                lastSyncedAt = existing?.lastSyncedAt,
                deleted = false,
                syncStatus = SyncStatus.DIRTY.name,
            ),
        )
    }

    override suspend fun delete(noteId: String) {
        val existing = dao.getById(noteId)
        if (existing?.serverId == null) {
            // 从未同步到后端：直接物理删除，无需 tombstone
            dao.deleteById(noteId)
        } else {
            // 已在后端存在：软删，待同步把删除传上去后再物理清理
            dao.markDeleted(noteId, System.currentTimeMillis())
        }
    }

    override suspend fun moveToTrash(noteId: String) {
        // 始终软删（打 tombstone），无论是否同步过——确保都进入回收站可恢复。
        dao.markDeleted(noteId, System.currentTimeMillis())
    }

    override suspend fun restore(noteId: String) {
        dao.restore(noteId, System.currentTimeMillis())
    }

    override suspend fun deleteForever(noteId: String) {
        dao.deleteById(noteId)
    }

    override suspend fun getNoteById(noteId: String): Note? =
        dao.getById(noteId)?.toNote()

    override suspend fun clearAll() = dao.clearAll()

    override suspend fun count(): Int = dao.count()
}
