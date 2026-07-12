package com.novamind.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.data.FolderRepository
import com.novamind.app.data.FoldersRepository
import com.novamind.app.data.NoteRepository
import com.novamind.app.feature.create.editor.NoteDocument
import com.novamind.app.feature.create.folder.RemoteFolder
import com.novamind.app.feature.create.model.Note
import com.novamind.app.feature.create.model.NoteItem
import com.novamind.app.util.ColorUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Library 页面 ViewModel。
 *
 * 文件夹列表**由服务端 `GET /folders` 驱动**（[FoldersRepository]）：create 走 `POST /folders`，
 * rename 走 `PATCH {name}`，删除走 `PATCH {trashed:true}`（移入回收站）。笔记仍来自本地
 * [NoteRepository]，按文件夹名聚合得到每个文件夹的 noteCount；文件夹颜色为本地概念（服务端不带），
 * 仍由 [FolderRepository]（Room）按名维护并在此合并显示。
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val foldersRepository: FoldersRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState = _uiState.asStateFlow()

    // 最近一次的领域笔记快照，供重命名 / 删除文件夹时改写笔记归属
    private var domainNotes: List<Note> = emptyList()

    // 服务端文件夹快照（GET /folders）；随 loadFolders 更新，驱动文件夹列表。
    private val serverFolders = MutableStateFlow<List<RemoteFolder>>(emptyList())

    init {
        // 三源合流：本地笔记（计数 + 卡片）× 本地文件夹（颜色兜底）× 服务端文件夹（存在性 / id / 排序）
        combine(noteRepository.notes, folderRepository.folders, serverFolders) { notes, stored, remote ->
            Triple(notes, stored, remote)
        }
            .onEach { (notes, stored, remote) ->
                domainNotes = notes
                // 按文件夹聚合：无文件夹的归入「Unfiled」
                val counts = notes.groupingBy { it.folder?.name ?: "Unfiled" }.eachCount()
                val colorByName = stored.associateBy { it.name }
                val remoteByName = remote.associateBy { it.name }
                // 文件夹存在性以「有笔记的分组」∪「服务端文件夹」为准
                val names = LinkedHashSet<String>().apply {
                    addAll(counts.keys)
                    addAll(remote.map { it.name })
                }
                val folders = names
                    .map { name ->
                        LibraryFolder(
                            id = remoteByName[name]?.id,
                            name = name,
                            noteCount = counts[name] ?: 0,
                            colorHex = colorByName[name]?.colorHex,
                        )
                    }
                    // 服务端文件夹按其 sortOrder 在前；其余（笔记派生 / Unfiled）按笔记数降序其后
                    .sortedWith(
                        compareBy(
                            { remoteByName[it.name]?.sortOrder ?: Int.MAX_VALUE },
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

        loadFolders()
    }

    /** 拉取服务端文件夹列表（GET /folders），刷新 [serverFolders]。 */
    fun loadFolders() {
        viewModelScope.launch {
            when (val r = foldersRepository.listFolders(limit = PAGE_SIZE)) {
                is ApiResult.Success -> serverFolders.value = r.data?.items.orEmpty()
                is ApiResult.BizError -> AppLog.w(TAG) { "loadFolders 业务错误 code=${r.code} traceId=${r.traceId}" }
                is ApiResult.NetworkError -> AppLog.w(TAG) { "loadFolders 网络错误: ${r.message}" }
            }
        }
    }

    /** 新建文件夹：POST /folders；颜色本地保存（服务端无颜色字段），成功后重拉列表。 */
    fun createFolder(name: String, colorHex: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            when (val r = foldersRepository.createFolder(trimmed)) {
                is ApiResult.Success -> {
                    folderRepository.create(trimmed, colorHex)   // 本地存颜色，供列表合并显示
                    loadFolders()
                }
                is ApiResult.BizError -> AppLog.w(TAG) { "createFolder 业务错误 code=${r.code} traceId=${r.traceId}" }
                is ApiResult.NetworkError -> AppLog.w(TAG) { "createFolder 网络错误: ${r.message}" }
            }
        }
    }

    /** 重命名文件夹：PATCH {name}；同步本地颜色记录与该文件夹下笔记的归属名，成功后重拉。 */
    fun renameFolder(oldName: String, newName: String) {
        val from = oldName.trim()
        val to = newName.trim()
        // 「Unfiled」为虚拟分组，不可改名
        if (from.isEmpty() || to.isEmpty() || from == "Unfiled" || from.equals(to, ignoreCase = true)) return
        val id = serverFolders.value.firstOrNull { it.name == from }?.id ?: run {
            AppLog.w(TAG) { "renameFolder 找不到服务端文件夹 name=$from" }
            return
        }
        viewModelScope.launch {
            when (val r = foldersRepository.renameFolder(id, to)) {
                is ApiResult.Success -> {
                    folderRepository.rename(from, to)
                    domainNotes.filter { it.folder?.name == from }.forEach { note ->
                        noteRepository.addOrUpdate(note.copy(folder = note.folder?.copy(name = to)))
                    }
                    loadFolders()
                }
                is ApiResult.BizError -> AppLog.w(TAG) { "renameFolder 业务错误 id=$id code=${r.code} traceId=${r.traceId}" }
                is ApiResult.NetworkError -> AppLog.w(TAG) { "renameFolder 网络错误 id=$id: ${r.message}" }
            }
        }
    }

    /** 删除文件夹：PATCH {trashed:true} 移入回收站；本地把其下笔记移到「未归档」并清理颜色记录，成功后重拉。 */
    fun deleteFolder(name: String) {
        val target = name.trim()
        if (target.isEmpty() || target == "Unfiled") return
        val id = serverFolders.value.firstOrNull { it.name == target }?.id ?: run {
            AppLog.w(TAG) { "deleteFolder 找不到服务端文件夹 name=$target" }
            return
        }
        viewModelScope.launch {
            when (val r = foldersRepository.setTrashed(id, trashed = true)) {
                is ApiResult.Success -> {
                    folderRepository.delete(target)
                    domainNotes.filter { it.folder?.name == target }.forEach { note ->
                        noteRepository.addOrUpdate(note.copy(folder = null))
                    }
                    loadFolders()
                }
                is ApiResult.BizError -> AppLog.w(TAG) { "deleteFolder 业务错误 id=$id code=${r.code} traceId=${r.traceId}" }
                is ApiResult.NetworkError -> AppLog.w(TAG) { "deleteFolder 网络错误 id=$id: ${r.message}" }
            }
        }
    }

    /**
     * 修改文件夹颜色：颜色为本地概念（服务端无该字段），仍持久化到本地库，随 Flow 立即刷新列表。
     */
    fun changeFolderColor(name: String, colorHex: String?) {
        viewModelScope.launch { folderRepository.setColor(name, colorHex) }
    }

    /**
     * 保存拖拽后的文件夹顺序：PUT /folders/order。把可见顺序里的文件夹名映射为服务端 id
     * （虚拟分组如 Unfiled 无 id、自动跳过），提交后重拉列表以服务端 sortOrder 呈现新顺序。
     */
    fun reorderFolders(orderedNames: List<String>) {
        val byName = serverFolders.value.associateBy { it.name }
        val orderedIds = orderedNames.mapNotNull { byName[it]?.id }
        if (orderedIds.isEmpty()) return
        viewModelScope.launch {
            when (val r = foldersRepository.reorderFolders(orderedIds)) {
                is ApiResult.Success -> loadFolders()
                is ApiResult.BizError -> AppLog.w(TAG) { "reorderFolders 业务错误 code=${r.code} traceId=${r.traceId}" }
                is ApiResult.NetworkError -> AppLog.w(TAG) { "reorderFolders 网络错误: ${r.message}" }
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

    private companion object {
        const val TAG = "LibraryVM"
        const val PAGE_SIZE = 100
    }
}
