package com.novamind.app.feature.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.NovieApplication
import com.novamind.app.feature.create.editor.NoteDocument
import com.novamind.app.feature.note.NoteItem
import com.novamind.app.util.ColorUtils
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
                // 按文件夹聚合：无文件夹的归入「Unfiled」，按笔记数降序
                val folders = notes
                    .groupingBy { it.folder?.name ?: "Unfiled" }
                    .eachCount()
                    .map { (name, count) -> LibraryFolder(name = name, noteCount = count) }
                    .sortedByDescending { it.noteCount }
                _uiState.update {
                    it.copy(
                        notes = notes.map { note ->
                            NoteItem(
                                id = note.id,
                                title = note.title,
                                description = NoteDocument.previewText(note.body),
                                tags = note.tags.map { it.name },
                                borderColor = ColorUtils.parseHexColor(note.borderColorHex),
                                imagePath = NoteDocument.firstImagePath(note.body),
                                folderName = note.folder?.name,
                                createdAt = note.createdAt,
                                updatedAt = note.updatedAt,
                            )
                        },
                        folders = folders,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /** 切换 Recent 页的网格 / 列表视图。 */
    fun toggleViewMode() {
        _uiState.update {
            it.copy(
                viewMode = if (it.viewMode == LibraryViewMode.GRID) {
                    LibraryViewMode.LIST
                } else {
                    LibraryViewMode.GRID
                }
            )
        }
    }
}
