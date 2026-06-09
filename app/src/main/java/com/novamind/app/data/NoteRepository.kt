package com.novamind.app.data

import com.novamind.app.feature.create.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    /** 全部笔记（实时 Flow，按更新时间倒序） */
    val notes: Flow<List<Note>>

    suspend fun addOrUpdate(note: Note)

    suspend fun delete(noteId: String)

    suspend fun getNoteById(noteId: String): Note?
}
