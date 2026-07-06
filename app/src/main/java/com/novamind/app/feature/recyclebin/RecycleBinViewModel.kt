package com.novamind.app.feature.recyclebin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.data.NoteRepository
import com.novamind.app.feature.create.editor.NoteDocument
import com.novamind.app.feature.create.model.NoteItem
import com.novamind.app.util.ColorUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 回收站 ViewModel：从 [com.novamind.app.data.NoteRepository] 实时派生已软删的笔记，
 * 支持恢复（restore）与彻底删除（deleteForever）。
 */
@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecycleBinUiState())
    val uiState = _uiState.asStateFlow()

    init {
        noteRepository.deletedNotes
            .onEach { notes ->
                _uiState.update {
                    it.copy(
                        notes = notes.map { note ->
                            NoteItem(
                                id = note.id,
                                title = note.title,
                                description = NoteDocument.previewText(note.body),
                                tags = note.tags.map { tag -> tag.name },
                                borderColor = ColorUtils.parseHexColor(note.borderColorHex),
                                imagePath = NoteDocument.firstImagePath(note.body),
                                folderName = note.folder?.name,
                                createdAt = note.createdAt,
                                // updatedAt 即软删时间，用于计算「剩余 N 天」
                                updatedAt = note.updatedAt,
                            )
                        },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /** 从回收站恢复笔记（回到正常列表）。 */
    fun restore(noteId: String) {
        viewModelScope.launch { noteRepository.restore(noteId) }
    }

    /** 彻底删除（物理删除，不可恢复）。 */
    fun deleteForever(noteId: String) {
        viewModelScope.launch { noteRepository.deleteForever(noteId) }
    }
}
