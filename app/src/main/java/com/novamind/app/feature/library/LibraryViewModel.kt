package com.novamind.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.data.FolderRepository
import com.novamind.app.data.FoldersRepository
import com.novamind.app.data.NotesRepository
import com.novamind.app.feature.create.folder.RemoteFolder
import com.novamind.app.feature.create.model.NoteItem
import com.novamind.app.feature.create.model.RemoteNoteSummary
import com.novamind.app.util.ColorUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
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
 * 笔记与文件夹均**由服务端驱动**，与首页保持一致：
 * - Recent 页笔记来自 `GET /notes`（[NotesRepository.listNotes]，活跃视图），与 [com.novamind.app.feature.home.HomeViewModel] 相同的映射（列表项不含正文，故 description/tags 留空）；
 * - 文件夹来自 `GET /folders`（[FoldersRepository]），create/rename/trash/reorder 走对应端点；
 * - 文件夹颜色为本地概念（服务端不带），仍由 [FolderRepository]（Room）按名维护并合并显示；
 * - 每个文件夹的 noteCount 由服务端笔记按 folderId → 文件夹名聚合得到。
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val folderRepository: FolderRepository,
    private val foldersRepository: FoldersRepository,
    private val notesRepository: NotesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState = _uiState.asStateFlow()

    // 服务端笔记快照（GET /notes，活跃视图）
    private val serverNotes = MutableStateFlow<List<RemoteNoteSummary>>(emptyList())
    // 服务端文件夹快照（GET /folders）
    private val serverFolders = MutableStateFlow<List<RemoteFolder>>(emptyList())

    init {
        // 三源合流：服务端笔记（列表 + 计数）× 本地文件夹（颜色兜底）× 服务端文件夹（存在性 / id / 排序）
        combine(serverNotes, folderRepository.folders, serverFolders) { notes, stored, remote ->
            Triple(notes, stored, remote)
        }
            .onEach { (notes, stored, remote) ->
                val folderNameById = remote.associate { it.id to it.name }
                // 按文件夹聚合：无文件夹（folderId=null）的归入「Unfiled」
                val counts = notes.groupingBy { folderNameById[it.folderId] ?: "Unfiled" }.eachCount()
                val colorByName = stored.associateBy { it.name }
                val remoteByName = remote.associateBy { it.name }
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
                    .sortedWith(
                        compareBy(
                            { remoteByName[it.name]?.sortOrder ?: Int.MAX_VALUE },
                            { -it.noteCount },
                        ),
                    )
                _uiState.update {
                    it.copy(
                        notes = notes.map { note -> note.toNoteItem(folderNameById[note.folderId]) },
                        folders = folders,
                    )
                }
            }
            .launchIn(viewModelScope)

        reload()
    }

    /** 静默重拉笔记与文件夹（每次进入 Library 时调用，与首页一致）。 */
    fun reload() {
        loadNotes()
        loadFolders()
    }

    /** 拉取服务端笔记列表（活跃视图，GET /notes）。 */
    private fun loadNotes() {
        viewModelScope.launch {
            when (val r = notesRepository.listNotes(trashed = false, limit = NOTES_PAGE_SIZE)) {
                is ApiResult.Success -> serverNotes.value = r.data?.items.orEmpty()
                is ApiResult.BizError -> AppLog.w(TAG) { "loadNotes 业务错误 code=${r.code} traceId=${r.traceId}" }
                is ApiResult.NetworkError -> AppLog.w(TAG) { "loadNotes 网络错误: ${r.message}" }
            }
        }
    }

    /** 拉取服务端文件夹列表（GET /folders），刷新 [serverFolders]。 */
    fun loadFolders() {
        viewModelScope.launch {
            when (val r = foldersRepository.listFolders(limit = FOLDERS_PAGE_SIZE)) {
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

    /**
     * 重命名文件夹：PATCH {name}。服务端文件夹以 id 为身份，改名后其下笔记的 folderId 不变、
     * 归属自动跟随，无需逐条改写笔记。本地颜色记录同步改名，成功后重拉。
     */
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
                    loadFolders()
                }
                is ApiResult.BizError -> AppLog.w(TAG) { "renameFolder 业务错误 id=$id code=${r.code} traceId=${r.traceId}" }
                is ApiResult.NetworkError -> AppLog.w(TAG) { "renameFolder 网络错误 id=$id: ${r.message}" }
            }
        }
    }

    /**
     * 删除文件夹：PATCH {trashed:true} 移入回收站（服务端要求文件夹为空，非空会 40910）。
     * 本地清理颜色记录，成功后重拉。
     */
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
                    loadFolders()
                }
                is ApiResult.BizError -> AppLog.w(TAG) { "deleteFolder 业务错误 id=$id code=${r.code} traceId=${r.traceId}" }
                is ApiResult.NetworkError -> AppLog.w(TAG) { "deleteFolder 网络错误 id=$id: ${r.message}" }
            }
        }
    }

    /** 修改文件夹颜色：颜色为本地概念（服务端无该字段），持久化到本地库，随 Flow 立即刷新列表。 */
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

    /** 列表项领域模型 → UI 模型。列表接口不含正文，故 description/tags/图片留空（与首页一致）。 */
    private fun RemoteNoteSummary.toNoteItem(folderName: String?): NoteItem = NoteItem(
        id = id,
        title = title.orEmpty(),
        description = "",
        tags = emptyList(),
        borderColor = ColorUtils.parseHexColor(borderColorHex),
        imagePath = null,
        folderName = folderName,
        createdAt = createdAt.toEpochMillisOrZero(),
        updatedAt = updatedAt.toEpochMillisOrZero(),
    )

    /** ISO-8601 → epoch 毫秒；空或解析失败回退 0。 */
    private fun String?.toEpochMillisOrZero(): Long =
        this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

    private companion object {
        const val TAG = "LibraryVM"
        const val NOTES_PAGE_SIZE = 50
        const val FOLDERS_PAGE_SIZE = 100
    }
}
