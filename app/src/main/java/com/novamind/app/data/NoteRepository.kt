package com.novamind.app.data

import com.novamind.app.feature.create.model.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 内存级笔记仓库，进程内单例共享。
 * 后续可替换为 Room 持久化，不影响上层 ViewModel。
 */
object NoteRepository {

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes = _notes.asStateFlow()

    fun addOrUpdate(note: Note) {
        _notes.update { list ->
            val idx = list.indexOfFirst { it.id == note.id }
            if (idx >= 0) list.toMutableList().also { it[idx] = note }
            else listOf(note) + list          // 最新笔记排最前
        }
    }

    fun delete(noteId: String) {
        _notes.update { list -> list.filter { it.id != noteId } }
    }
}
