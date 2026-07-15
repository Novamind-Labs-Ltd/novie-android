package com.novamind.app.data

import com.novamind.app.feature.create.model.Note
import kotlinx.coroutines.flow.Flow

interface LocalNoteRepository {
    /** 全部笔记（实时 Flow，按更新时间倒序） */
    val notes: Flow<List<Note>>

    /** 回收站笔记（已软删的 tombstone，实时 Flow，按删除时间倒序） */
    val deletedNotes: Flow<List<Note>>

    suspend fun addOrUpdate(note: Note)

    suspend fun delete(noteId: String)

    /** 移入回收站（软删 tombstone，可在回收站恢复或彻底删除）。 */
    suspend fun moveToTrash(noteId: String)

    /** 从回收站恢复笔记。 */
    suspend fun restore(noteId: String)

    /** 彻底删除（物理删除，不可恢复）。 */
    suspend fun deleteForever(noteId: String)

    suspend fun getNoteById(noteId: String): Note?

    /** 清空全部笔记（Debug 工具用） */
    suspend fun clearAll()

    /** 笔记总数（Debug 工具用） */
    suspend fun count(): Int
}
