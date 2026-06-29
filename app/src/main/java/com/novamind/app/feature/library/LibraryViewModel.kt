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
import kotlinx.coroutines.flow.combine
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

    // 本会话内手动创建的文件夹「名称 → 颜色 hex」（无独立文件夹存储，故仅会话级）
    private val createdFolders = MutableStateFlow<Map<String, String?>>(emptyMap())

    // 用户手动拖拽得到的文件夹顺序（名称序，会话级）；空 = 用默认排序（按笔记数降序）
    private val folderOrder = MutableStateFlow<List<String>>(emptyList())

    init {
        combine(noteRepository.notes, createdFolders, folderOrder) { notes, extra, order ->
            Triple(notes, extra, order)
        }
            .onEach { (notes, extra, order) ->
                // 按文件夹聚合：无文件夹的归入「Unfiled」；并入手动创建的空文件夹（count 0）
                val counts = notes.groupingBy { it.folder?.name ?: "Unfiled" }.eachCount()
                val names = LinkedHashSet<String>().apply {
                    addAll(counts.keys)
                    addAll(extra.keys)
                }
                // 排序：已手动排序的按其顺序在前；其余（含新建/新出现的）按笔记数降序排其后
                val orderIndex = order.withIndex().associate { (i, n) -> n to i }
                val folders = names
                    .map { name -> LibraryFolder(name = name, noteCount = counts[name] ?: 0, colorHex = extra[name]) }
                    .sortedWith(compareBy({ orderIndex[it.name] ?: Int.MAX_VALUE }, { -it.noteCount }))
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

    /**
     * 创建新文件夹：当前无独立文件夹存储，先在本会话内登记一个空文件夹（noteCount = 0），
     * 立即显示在 Folders 页；后续把笔记归入该文件夹即落库。colorHex 暂未持久化。
     */
    fun createFolder(name: String, colorHex: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        createdFolders.update { existing ->
            // 已存在同名（忽略大小写）则保留原项；否则登记新文件夹及其颜色
            if (existing.keys.any { it.equals(trimmed, ignoreCase = true) }) existing
            else existing + (trimmed to colorHex)
        }
    }

    /** 保存用户拖拽后的文件夹顺序（名称序，会话级）。 */
    fun reorderFolders(orderedNames: List<String>) {
        folderOrder.value = orderedNames
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
