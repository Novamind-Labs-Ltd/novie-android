package com.novamind.app.data

import com.novamind.app.data.db.NoteDao
import com.novamind.app.data.db.toEntity
import com.novamind.app.data.db.toNote
import com.novamind.app.feature.create.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomNoteRepository(private val dao: NoteDao) : NoteRepository {

    override val notes: Flow<List<Note>> =
        dao.getAllNotes().map { entities -> entities.map { it.toNote() } }

    override suspend fun addOrUpdate(note: Note) {
        dao.upsert(note.toEntity())
    }

    override suspend fun delete(noteId: String) {
        dao.deleteById(noteId)
    }

    override suspend fun getNoteById(noteId: String): Note? =
        dao.getById(noteId)?.toNote()
}
