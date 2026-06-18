package com.novamind.app.feature.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.NovieApplication
import com.novamind.app.feature.create.editor.NoteDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/**
 * Library 页面 ViewModel：从 [com.novamind.app.data.NoteRepository] 实时派生笔记列表，
 * 空/非空由数据决定（Room Flow 驱动，新增/删除笔记自动反映到空状态切换）。
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val noteRepository = (application as NovieApplication).noteRepository

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState = _uiState.asStateFlow()

    init {
        noteRepository.notes
            .onEach { notes ->
                _uiState.update {
                    it.copy(
                        notes = notes.map { note ->
                            LibraryNote(
                                id = note.id,
                                title = note.title.ifBlank { "Untitled" },
                                preview = NoteDocument.previewText(note.body),
                                tag = note.tags.firstOrNull()?.name
                                    ?: note.folder?.name
                                    ?: "Note",
                            )
                        }
                    )
                }
            }
            .launchIn(viewModelScope)
    }
}
