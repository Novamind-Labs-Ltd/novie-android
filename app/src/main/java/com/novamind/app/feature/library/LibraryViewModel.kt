package com.novamind.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.common.config.AppConfig
import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.BizCode
import com.novamind.app.common.net.response.fold
import com.novamind.app.data.FolderRepository
import com.novamind.app.data.FoldersRepository
import com.novamind.app.data.RemoteNoteRepository
import com.novamind.app.feature.create.folder.RemoteFolder
import com.novamind.app.feature.create.model.NoteItem
import com.novamind.app.feature.create.model.RemoteNoteSummary
import com.novamind.app.util.ColorUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Library 页面 ViewModel。
 *
 * 笔记与文件夹均**由服务端驱动**，与首页保持一致：
 * - Recent 页笔记来自 `GET /notes`（[RemoteNoteRepository.listNotes]，活跃视图），与 [com.novamind.app.feature.home.HomeViewModel] 相同的映射（列表项不含正文，故 description/tags 留空）；
 * - 文件夹来自 `GET /folders`（[FoldersRepository]），create/rename/trash/reorder 走对应端点；
 * - 文件夹颜色为本地概念（服务端不带），由 [FolderRepository]（Room）按服务端 folderId 维护；
 * - 每个文件夹的 noteCount 直接使用 `GET /folders` 返回的活跃笔记数，不依赖 Recent 的加载分页。
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val folderRepository: FolderRepository,
    private val foldersRepository: FoldersRepository,
    private val notesRepository: RemoteNoteRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState = _uiState.asStateFlow()

    // 服务端笔记快照（GET /notes，活跃视图）
    private val serverNotes = MutableStateFlow<List<RemoteNoteSummary>>(emptyList())
    // 服务端文件夹快照（GET /folders）
    private val serverFolders = MutableStateFlow<List<RemoteFolder>>(emptyList())
    // 文件夹详情页：当前文件夹内笔记快照（按 folderId 独立拉取，与 Recent 解耦）
    private val folderNotesRaw = MutableStateFlow<List<RemoteNoteSummary>>(emptyList())
    private var notesCursor: String? = null
    private var foldersCursor: String? = null
    private var notesGeneration = 0L
    private var foldersGeneration = 0L
    private var notesRequestJob: Job? = null
    private var foldersRequestJob: Job? = null
    private var refreshJob: Job? = null
    private var reorderGeneration = 0L
    /**
     * 串行化所有会读写 [serverFolders] 的远端请求。
     *
     * 仅靠 foldersGeneration / reorderGeneration 只能阻止同类旧响应；共用互斥锁后，刷新与分页会在
     * 已经发出的 reorder 完成后再读取服务端，reorder 则会先取消旧读取，避免跨请求覆盖新状态。
     */
    private val foldersRequestMutex = Mutex()

    init {
        // 三源合流：服务端笔记列表 × 本地文件夹颜色 × 服务端文件夹（存在性 / id / 排序 / 计数）
        combine(serverNotes, folderRepository.folders, serverFolders) { notes, stored, remote ->
            val folderNameById = remote.associate { it.id to it.name }
            val colorById = stored.associateBy { it.id }
            // 文件夹列表仅取服务端文件夹（不再有虚拟的 Unfiled 分组），按 sortOrder 排序
            val folders = remote
                .sortedWith(compareBy({ it.sortOrder }, { it.name }))
                .map { rf ->
                    LibraryFolder(
                        id = rf.id,
                        name = rf.name,
                        noteCount = rf.noteCount.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                        colorHex = colorById[rf.id]?.colorHex,
                    )
                }
            notes.map { note -> note.toNoteItem(folderNameById[note.folderId]) } to folders
        }
            .onEach { (noteItems, folders) ->
                _uiState.update { it.copy(notes = noteItems, folders = folders) }
            }
            .launchIn(viewModelScope)

        // 文件夹详情笔记合流：原始快照 × 服务端文件夹名称映射
        combine(folderNotesRaw, serverFolders) { raw, remote ->
            val folderNameById = remote.associate { it.id to it.name }
            raw.map { it.toNoteItem(folderNameById[it.folderId]) }
        }
            .onEach { items ->
                _uiState.update { it.copy(folderNotes = items) }
            }
            .launchIn(viewModelScope)

    }

    /** 静默重拉笔记与文件夹（每次进入 Library 时调用，与首页一致）。 */
    fun reload() {
        cancelRefresh()
        startNotesFirstPage()
        startFoldersFirstPage()
    }

    /** 下拉刷新（Recent 页）：重拉笔记与文件夹，两者都结束后再关闭刷新态（与首页一致）。 */
    fun onRefresh() {
        if (_uiState.value.isRefreshing) return
        notesRequestJob?.cancel()
        foldersRequestJob?.cancel()
        val notesRequestGeneration = ++notesGeneration
        val foldersRequestGeneration = ++foldersGeneration
        // 新读取接管文件夹状态，尚未应用的 reorder 响应必须失效。
        ++reorderGeneration
        notesCursor = null
        foldersCursor = null
        _uiState.update {
            it.copy(
                isRefreshing = true,
                isLoadingMore = false,
                isLoadingMoreFolders = false,
                hasMoreNotes = false,
                hasMoreFolders = false,
            )
        }
        val notesJob = viewModelScope.launch { fetchNotes(notesRequestGeneration) }
        val foldersJob = viewModelScope.launch { fetchFolders(foldersRequestGeneration) }
        notesRequestJob = notesJob
        foldersRequestJob = foldersJob
        refreshJob = viewModelScope.launch {
            notesJob.join()
            foldersJob.join()
            if (notesGeneration == notesRequestGeneration && foldersGeneration == foldersRequestGeneration) {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun cancelRefresh() {
        refreshJob?.cancel()
        refreshJob = null
        if (_uiState.value.isRefreshing) {
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /** 拉取服务端文件夹列表（GET /folders），刷新 [serverFolders]。 */
    fun loadFolders() {
        cancelRefresh()
        startFoldersFirstPage()
    }

    private fun startNotesFirstPage() {
        notesRequestJob?.cancel()
        val generation = ++notesGeneration
        notesCursor = null
        _uiState.update { it.copy(hasMoreNotes = false, isLoadingMore = false) }
        notesRequestJob = viewModelScope.launch { fetchNotes(generation) }
    }

    private fun startFoldersFirstPage() {
        foldersRequestJob?.cancel()
        val generation = ++foldersGeneration
        // 新读取接管文件夹状态，尚未应用的 reorder 响应必须失效。
        ++reorderGeneration
        foldersCursor = null
        _uiState.update { it.copy(hasMoreFolders = false, isLoadingMoreFolders = false) }
        foldersRequestJob = viewModelScope.launch { fetchFolders(generation) }
    }

    /** 拉取第一页笔记（刷新 / 首次进入）：重置分页游标并整表替换。 */
    private suspend fun fetchNotes(generation: Long) {
        notesRepository.listNotes(
            trashed = false,
            limit = AppConfig.Paging.LIBRARY_RECENT_PAGE_SIZE,
            cursor = null,
        ).fold(
            onSuccess = { page ->
                if (notesGeneration == generation) {
                    serverNotes.value = page?.items.orEmpty()
                    notesCursor = page?.nextCursor
                    _uiState.update {
                        it.copy(hasMoreNotes = page?.nextCursor != null, isLoadingMore = false, isInitialLoading = false)
                    }
                }
            },
            // 首次拉取无论成败都结束首屏加载态：失败则由骨架切到（空）内容/空态，不再卡骨架
            onFail = {
                if (notesGeneration == generation) {
                    logApiError("loadNotes", it)
                    _uiState.update { it.copy(isInitialLoading = false, isLoadingMore = false) }
                }
            },
        )
    }

    /** 上拉加载下一页：按游标取下 20 条并追加到现有列表（Recent 页触底时调用）。 */
    fun loadMoreNotes() {
        val cursor = notesCursor
        val generation = notesGeneration
        // 无更多 / 正在加载 / 正在刷新 时不重复触发
        if (cursor == null || _uiState.value.isLoadingMore || _uiState.value.isRefreshing) return
        _uiState.update { it.copy(isLoadingMore = true) }
        notesRequestJob = viewModelScope.launch {
            notesRepository.listNotes(
                trashed = false,
                limit = AppConfig.Paging.LIBRARY_RECENT_PAGE_SIZE,
                cursor = cursor,
            ).fold(
                onSuccess = { page ->
                    if (notesGeneration == generation && notesCursor == cursor) {
                        // 追加去重（按 id），避免游标边界重复
                        val existing = serverNotes.value
                        val seen = existing.mapTo(HashSet()) { it.id }
                        serverNotes.value = existing + page?.items.orEmpty().filter { seen.add(it.id) }
                        notesCursor = page?.nextCursor
                        _uiState.update { it.copy(hasMoreNotes = page?.nextCursor != null, isLoadingMore = false) }
                    }
                },
                onFail = {
                    if (notesGeneration == generation && notesCursor == cursor) {
                        logApiError("loadMoreNotes", it)
                        _uiState.update { it.copy(isLoadingMore = false) }
                    }
                },
            )
        }
    }

    /** 拉取第一页文件夹（刷新 / 首次进入）：重置分页游标并整表替换。 */
    private suspend fun fetchFolders(generation: Long) {
        foldersRequestMutex.withLock {
            if (foldersGeneration != generation) return@withLock
            foldersRepository.listFolders(
                limit = AppConfig.Paging.LIBRARY_FOLDERS_PAGE_SIZE,
                cursor = null,
            ).fold(
                onSuccess = { page ->
                    if (foldersGeneration == generation) {
                        serverFolders.value = page?.items.orEmpty()
                        foldersCursor = page?.nextCursor
                        _uiState.update { it.copy(hasMoreFolders = page?.nextCursor != null, isLoadingMoreFolders = false) }
                    }
                },
                onFail = {
                    if (foldersGeneration == generation) {
                        logApiError("loadFolders", it)
                        _uiState.update { it.copy(isLoadingMoreFolders = false) }
                    }
                },
            )
        }
    }

    /** 上拉加载下一页文件夹：按游标取下 20 条并追加（Folders 页触底时调用）。 */
    fun loadMoreFolders() {
        val cursor = foldersCursor
        val generation = foldersGeneration
        if (cursor == null || _uiState.value.isLoadingMoreFolders || _uiState.value.isRefreshing) return
        _uiState.update { it.copy(isLoadingMoreFolders = true) }
        foldersRequestJob = viewModelScope.launch {
            foldersRequestMutex.withLock {
                if (foldersGeneration != generation || foldersCursor != cursor) return@withLock
                foldersRepository.listFolders(
                    limit = AppConfig.Paging.LIBRARY_FOLDERS_PAGE_SIZE,
                    cursor = cursor,
                ).fold(
                    onSuccess = { page ->
                        if (foldersGeneration == generation && foldersCursor == cursor) {
                            val existing = serverFolders.value
                            val seen = existing.mapTo(HashSet()) { it.id }
                            serverFolders.value = existing + page?.items.orEmpty().filter { seen.add(it.id) }
                            foldersCursor = page?.nextCursor
                            _uiState.update { it.copy(hasMoreFolders = page?.nextCursor != null, isLoadingMoreFolders = false) }
                        }
                    },
                    onFail = {
                        if (foldersGeneration == generation && foldersCursor == cursor) {
                            logApiError("loadMoreFolders", it)
                            _uiState.update { it.copy(isLoadingMoreFolders = false) }
                        }
                    },
                )
            }
        }
    }

    // ── 文件夹详情：按 folderId 独立拉取文件夹内笔记（分页，与 Recent 解耦）──────────
    private var folderNotesCursor: String? = null
    private var currentFolderId: String? = null
    private var folderNotesJob: Job? = null

    /** 打开文件夹详情：直接使用服务端 folderId 拉取第一页笔记。 */
    fun openFolder(folderId: String) {
        folderNotesJob?.cancel()
        val fid = folderId.takeIf { id -> serverFolders.value.any { it.id == id } }
        currentFolderId = fid
        folderNotesCursor = null
        folderNotesRaw.value = emptyList()
        _uiState.update {
            it.copy(folderNotes = emptyList(), folderNotesHasMore = false, folderNotesLoading = fid != null)
        }
        if (fid == null) {
            AppLog.w(TAG) { "openFolder 找不到服务端文件夹 id=$folderId" }
            return
        }
        folderNotesJob = viewModelScope.launch {
            notesRepository.listNotes(
                trashed = false,
                folderId = fid,
                limit = AppConfig.Paging.LIBRARY_RECENT_PAGE_SIZE,
                cursor = null,
            ).fold(
                onSuccess = { page ->
                    // 用户可能已切换或关闭文件夹；旧请求不得覆盖当前详情。
                    if (currentFolderId == fid) {
                        folderNotesRaw.value = page?.items.orEmpty()
                        folderNotesCursor = page?.nextCursor
                        _uiState.update {
                            it.copy(folderNotesHasMore = page?.nextCursor != null, folderNotesLoading = false)
                        }
                    }
                },
                onFail = {
                    if (currentFolderId == fid) {
                        logApiError("openFolder id=$fid", it)
                        _uiState.update { it.copy(folderNotesLoading = false) }
                    }
                },
            )
        }
    }

    /** 文件夹详情上拉加载下一页。 */
    fun loadMoreFolderNotes() {
        val cursor = folderNotesCursor
        val fid = currentFolderId
        if (fid == null || cursor == null || _uiState.value.folderNotesLoading) return
        _uiState.update { it.copy(folderNotesLoading = true) }
        folderNotesJob = viewModelScope.launch {
            notesRepository.listNotes(
                trashed = false,
                folderId = fid,
                limit = AppConfig.Paging.LIBRARY_RECENT_PAGE_SIZE,
                cursor = cursor,
            ).fold(
                onSuccess = { page ->
                    // 仅当前文件夹的当前游标请求可以追加，防止切换/返回后的旧分页响应污染列表。
                    if (currentFolderId == fid && folderNotesCursor == cursor) {
                        val existing = folderNotesRaw.value
                        val seen = existing.mapTo(HashSet()) { it.id }
                        folderNotesRaw.value = existing + page?.items.orEmpty().filter { seen.add(it.id) }
                        folderNotesCursor = page?.nextCursor
                        _uiState.update {
                            it.copy(folderNotesHasMore = page?.nextCursor != null, folderNotesLoading = false)
                        }
                    }
                },
                onFail = {
                    if (currentFolderId == fid && folderNotesCursor == cursor) {
                        logApiError("loadMoreFolderNotes id=$fid", it)
                        _uiState.update { it.copy(folderNotesLoading = false) }
                    }
                },
            )
        }
    }

    /** 关闭文件夹详情：清空该文件夹笔记快照与游标。 */
    fun closeFolder() {
        folderNotesJob?.cancel()
        folderNotesJob = null
        currentFolderId = null
        folderNotesCursor = null
        folderNotesRaw.value = emptyList()
        _uiState.update { it.copy(folderNotes = emptyList(), folderNotesHasMore = false, folderNotesLoading = false) }
    }

    /** 新建文件夹：POST /folders；颜色本地保存（服务端无颜色字段），成功后重拉列表。 */
    fun createFolder(name: String, colorHex: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            foldersRepository.createFolder(trimmed).fold(
                onSuccess = { created ->
                    if (created != null) {
                        folderRepository.upsert(created.id, created.name, colorHex)
                    }
                    loadFolders()
                },
                onFail = { logApiError("createFolder", it) },
            )
        }
    }

    /**
     * 重命名文件夹：PATCH {name}。服务端文件夹以 id 为身份，改名后其下笔记的 folderId 不变、
     * 归属自动跟随，无需逐条改写笔记。本地颜色记录同步改名，成功后重拉。
     */
    fun renameFolder(folderId: String, newName: String) {
        val to = newName.trim()
        val folder = serverFolders.value.firstOrNull { it.id == folderId } ?: run {
            AppLog.w(TAG) { "renameFolder 找不到服务端文件夹 id=$folderId" }
            return
        }
        if (to.isEmpty() || folder.name.equals(to, ignoreCase = true)) return
        viewModelScope.launch {
            foldersRepository.renameFolder(folderId, to).fold(
                onSuccess = {
                    folderRepository.rename(folderId, to)
                    loadFolders()
                },
                onFail = { logApiError("renameFolder id=$folderId", it) },
            )
        }
    }

    /**
     * 删除文件夹：PATCH {trashed:true} 移入回收站（服务端要求文件夹为空，非空会 40910）。
     * 本地清理颜色记录，成功后重拉。
     */
    fun deleteFolder(folderId: String) {
        val folder = serverFolders.value.firstOrNull { it.id == folderId } ?: run {
            AppLog.w(TAG) { "deleteFolder 找不到服务端文件夹 id=$folderId" }
            return
        }
        viewModelScope.launch {
            foldersRepository.setTrashed(folderId, trashed = true).fold(
                onSuccess = {
                    folderRepository.delete(folderId)
                    loadFolders()
                },
                onFail = { result ->
                    if (result is ApiResult.BizError && result.code == BizCode.FOLDER_NOT_EMPTY) {
                        // noteCount 只统计活跃笔记；后端删除检查还包含回收站笔记，以事务内结果为最终真值。
                        _uiState.update { it.copy(cannotDeleteFolderName = folder.name) }
                    } else {
                        logApiError("deleteFolder id=$folderId", result)
                    }
                },
            )
        }
    }

    fun dismissCannotDeleteFolder() {
        _uiState.update { it.copy(cannotDeleteFolderName = null) }
    }

    /** 修改文件夹颜色：颜色为本地概念（服务端无该字段），持久化到本地库，随 Flow 立即刷新列表。 */
    fun changeFolderColor(folderId: String, colorHex: String?) {
        val folder = serverFolders.value.firstOrNull { it.id == folderId } ?: return
        viewModelScope.launch { folderRepository.setColor(folderId, folder.name, colorHex) }
    }

    /**
     * 保存拖拽后的文件夹顺序：PUT /folders/order。UI 直接回传服务端 id 顺序，
     * 成功后采用接口返回的权威首屏与分页游标。
     */
    fun reorderFolders(orderedFolderIds: List<String>) {
        val byId = serverFolders.value.associateBy { it.id }
        val orderedIds = orderedFolderIds.distinct().filter { it in byId }
        if (orderedIds.isEmpty()) return
        cancelRefresh()
        // reorder 接管文件夹状态：取消列表请求并使其即使已返回也无法再落入 UI。
        foldersRequestJob?.cancel()
        foldersRequestJob = null
        val foldersRequestGeneration = ++foldersGeneration
        val generation = ++reorderGeneration
        foldersCursor = null
        _uiState.update { it.copy(hasMoreFolders = false, isLoadingMoreFolders = false) }
        // 先改 UI（乐观更新）：按新顺序重排 serverFolders 并重写 sortOrder，列表立即呈现新顺序、不等网络
        serverFolders.value = orderedIds
            .mapNotNull { byId[it] }
            .mapIndexed { i, f -> f.copy(sortOrder = i) }
        // 串行提交：前一个请求结束后只发送最新等待顺序；旧响应不得覆盖最后一次拖拽结果。
        viewModelScope.launch {
            foldersRequestMutex.withLock {
                // 等锁期间又发生了拖拽，则当前顺序已过期，直接跳过网络提交。
                if (reorderGeneration != generation || foldersGeneration != foldersRequestGeneration) return@withLock
                foldersRepository.reorderFolders(orderedIds).fold(
                    onSuccess = { page ->
                        if (reorderGeneration == generation && foldersGeneration == foldersRequestGeneration) {
                            serverFolders.value = page?.items.orEmpty()
                            foldersCursor = page?.nextCursor
                            _uiState.update {
                                it.copy(
                                    hasMoreFolders = page?.nextCursor != null,
                                    isLoadingMoreFolders = false,
                                )
                            }
                        }
                    },
                    onFail = {
                        if (reorderGeneration == generation && foldersGeneration == foldersRequestGeneration) {
                            logApiError("reorderFolders 回滚", it)
                            loadFolders()
                        }
                    },
                )
            }
        }
    }

    /** 仅记录 API 错误日志（本页各拉取/写操作均只记日志，不弹提示）。[op] 供定位。 */
    private fun logApiError(op: String, r: ApiResult<*>) {
        when (r) {
            is ApiResult.BizError -> AppLog.w(TAG) { "$op 业务错误 code=${r.code} traceId=${r.traceId}" }
            is ApiResult.NetworkError -> AppLog.w(TAG) { "$op 网络错误: ${r.message}" }
            is ApiResult.Success -> Unit
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

    /** 列表项领域模型 → UI 模型；缩略图只使用列表接口返回的 thumbnailUrl。 */
    private fun RemoteNoteSummary.toNoteItem(folderName: String?): NoteItem {
        val updated = updatedAt.toEpochMillisOrZero()
        return NoteItem(
            id = id,
            title = title.orEmpty(),
            // 去掉正文中的图片占位符「[Image]」文案：缩略图已直接展示图片，占位文案冗余
            preview = preview.orEmpty()
                .replace(Regex("\\[image]", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+"), " ")
                .trim(),
            tags = emptyList(),
            borderColor = ColorUtils.parseHexColor(borderColorHex),
            imagePath = thumbnailUrl,
            folderName = folderName,
            createdAt = createdAt.toEpochMillisOrZero(),
            updatedAt = updated,
        )
    }

    /** ISO-8601 → epoch 毫秒；空或解析失败回退 0。 */
    private fun String?.toEpochMillisOrZero(): Long =
        this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

    private companion object {
        const val TAG = "LibraryVM"
    }
}
