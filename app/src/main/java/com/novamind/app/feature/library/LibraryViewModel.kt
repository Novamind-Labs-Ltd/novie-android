package com.novamind.app.feature.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.NovieApplication
import com.novamind.app.feature.create.editor.NoteDocument
import com.novamind.app.feature.create.model.Note
import com.novamind.app.feature.note.NoteItem
import com.novamind.app.util.ColorUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Library 页面 ViewModel：从 [com.novamind.app.data.NoteRepository] 实时派生笔记列表，
 * 并结合 [com.novamind.app.data.FolderRepository] 中持久化的文件夹（含颜色与排序）。
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val noteRepository = (application as NovieApplication).noteRepository
    private val folderRepository = (application as NovieApplication).folderRepository

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState = _uiState.asStateFlow()

    // 最近一次的领域笔记快照，供重命名 / 删除文件夹时改写笔记归属
    private var domainNotes: List<Note> = emptyList()

    init {
        combine(noteRepository.notes, folderRepository.folders) { notes, stored ->
            notes to stored
        }
            .onEach { (notes, stored) ->
                domainNotes = notes
                // 按文件夹聚合：无文件夹的归入「Unfiled」；并入持久化文件夹（可能 count 0）
                val counts = notes.groupingBy { it.folder?.name ?: "Unfiled" }.eachCount()
                val storedByName = stored.associateBy { it.name }
                val names = LinkedHashSet<String>().apply {
                    addAll(counts.keys)
                    addAll(stored.map { it.name })
                }
                // 排序：持久化文件夹按其 sortIndex 在前；其余（笔记派生 / Unfiled）按笔记数降序排其后
                val folders = names
                    .map { name ->
                        LibraryFolder(
                            name = name,
                            noteCount = counts[name] ?: 0,
                            colorHex = storedByName[name]?.colorHex,
                        )
                    }
                    .sortedWith(
                        compareBy(
                            { storedByName[it.name]?.sortIndex ?: Int.MAX_VALUE },
                            { -it.noteCount },
                        ),
                    )
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

    /** 新建文件夹：持久化到数据库（含颜色），随 Flow 立即显示在 Folders 页。 */
    fun createFolder(name: String, colorHex: String? = null) {
        if (name.isBlank()) return
        viewModelScope.launch { folderRepository.create(name, colorHex) }
    }

    /** 保存用户拖拽后的文件夹顺序（持久化 sortIndex）。 */
    fun reorderFolders(orderedNames: List<String>) {
        viewModelScope.launch { folderRepository.setOrder(orderedNames) }
    }

    /** 修改文件夹颜色（持久化）。 */
    fun changeFolderColor(name: String, colorHex: String?) {
        viewModelScope.launch { folderRepository.setColor(name, colorHex) }
    }

    /** 重命名文件夹：改写该文件夹下所有笔记的归属名，并同步持久化文件夹记录。 */
    fun renameFolder(oldName: String, newName: String) {
        val from = oldName.trim()
        val to = newName.trim()
        // 「Unfiled」为虚拟分组，不可改名
        if (from.isEmpty() || to.isEmpty() || from == "Unfiled" || from.equals(to, ignoreCase = true)) return
        viewModelScope.launch {
            folderRepository.rename(from, to)
            domainNotes.filter { it.folder?.name == from }.forEach { note ->
                noteRepository.addOrUpdate(note.copy(folder = note.folder?.copy(name = to)))
            }
        }
    }

    /** 删除文件夹：把其下笔记移到「未归档」（folder = null），并删除持久化文件夹记录。 */
    fun deleteFolder(name: String) {
        val target = name.trim()
        if (target.isEmpty() || target == "Unfiled") return
        viewModelScope.launch {
            folderRepository.delete(target)
            domainNotes.filter { it.folder?.name == target }.forEach { note ->
                noteRepository.addOrUpdate(note.copy(folder = null))
            }
        }
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
