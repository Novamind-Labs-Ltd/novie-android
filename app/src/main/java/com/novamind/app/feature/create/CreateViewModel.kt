package com.novamind.app.feature.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.common.config.AppConfig
import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.data.AttachmentsRepository
import com.novamind.app.data.FilesRepository
import com.novamind.app.data.FolderRepository
import com.novamind.app.data.NotesRepository
import com.novamind.app.data.TagRepository
import com.novamind.app.feature.create.editor.NoteDocument
import com.novamind.app.feature.create.folder.Folder
import com.novamind.app.feature.create.tag.Tag
import com.novamind.app.util.ColorUtils
import com.novamind.app.util.ColorUtils.toHex
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.File
import java.time.Instant

private data class TextSnapshot(val title: String, val body: String)

@OptIn(FlowPreview::class)
@HiltViewModel
class CreateViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
    private val folderRepository: FolderRepository,
    private val tagRepository: TagRepository,
    private val filesRepository: FilesRepository,
    private val attachmentsRepository: AttachmentsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateUiState())
    val uiState = _uiState.asStateFlow()

    // 持久化文件夹/标签的最新快照（供 reset/loadNote 重建 state 时保留可选列表）
    private var availableFolders: List<Folder> = emptyList()
    private var availableTags: List<Tag> = emptyList()

    private val _navigateBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateBack = _navigateBack.asSharedFlow()

    // 保存失败的一次性事件（供 UI 弹 Toast）；用 SharedFlow 避免重组时重复提示。
    private val _saveError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val saveError = _saveError.asSharedFlow()

    // 图片上传并发限流（一次多选最多 5 张，限 AppConfig.Media.MAX_UPLOAD_CONCURRENCY 并发、其余排队）。
    private val uploadSemaphore = Semaphore(AppConfig.Media.MAX_UPLOAD_CONCURRENCY)

    // 已挂载到该笔记的附件 fileId 集合（loadNote 从服务端拉取初始化；saveNow 对账时增删）。
    private val attachedFileIds = mutableSetOf<String>()

    // 附件 fileId → 签名下载 URL（loadNote 后由 GET attachments 提供，供编辑器渲染兜底）。
    private val _attachmentUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val attachmentUrls: StateFlow<Map<String, String>> = _attachmentUrls.asStateFlow()

    private val undoStack = ArrayDeque<TextSnapshot>()
    private val redoStack = ArrayDeque<TextSnapshot>()

    // 保存触发器：所有变更只发一个信号，由下面两条流去重/限频后落盘。
    private val saveTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    // 串行化保存，避免防抖保存与封顶保存并发写、乱序。
    private val saveMutex = Mutex()

    // 服务端笔记的乐观锁版本号：POST 成功后为 1，PUT 成功后累加；新建未保存时为 null。
    // PUT /notes/{id} 需带上手上的 rev 做 latest-wins 判断。
    private var remoteRev: Long? = null

    // 上次成功保存到服务端的标题/正文快照：内容未变则跳过重复 POST/PUT。
    private var savedSnapshot: TextSnapshot? = null

    init {
        // 防抖：停顿超过 AUTO_SAVE_DELAY_MS 才落盘（大多数保存走这条）
        saveTrigger
            .debounce(AppConfig.Editor.AUTO_SAVE_DELAY_MS)
            .onEach { saveNow() }
            .launchIn(viewModelScope)
        // 封顶：持续编辑不停手时，最多每隔 SAVE_MAX_INTERVAL_MS 强制落盘一次
        saveTrigger
            .sample(AppConfig.Editor.SAVE_MAX_INTERVAL_MS)
            .onEach { saveNow() }
            .launchIn(viewModelScope)
        // 文件夹选择列表来自持久化文件夹库（实时）
        folderRepository.folders
            .onEach { stored ->
                val folders = stored.map { Folder(id = it.id, name = it.name) }
                availableFolders = folders
                _uiState.update { it.copy(availableFolders = folders) }
            }
            .launchIn(viewModelScope)
        // 标签选择列表来自持久化标签库（实时）
        tagRepository.tags
            .onEach { stored ->
                val tags = stored.map { Tag(id = it.id, name = it.name, colorHex = it.colorHex) }
                availableTags = tags
                _uiState.update { it.copy(availableTags = tags) }
            }
            .launchIn(viewModelScope)
    }

    // ── 初始化 / 重置 ─────────────────────────────────────────────────────────

    fun reset() {
        undoStack.clear()
        redoStack.clear()
        remoteRev = null
        savedSnapshot = null
        attachedFileIds.clear()
        _attachmentUrls.value = emptyMap()
        _uiState.value =
            CreateUiState(availableFolders = availableFolders, availableTags = availableTags)
    }

    /**
     * 按 id 从**服务端**加载笔记（GET /notes/{id}）填充编辑器。
     *
     * 服务端 NoteView 不含本地的标签/文件夹（这些目前只在本地库），故 selectedTags/selectedFolder 留空；
     * 正文按 App content 约定 `{"body": <文档字符串>}` 从 [RemoteNote.content] 抽取。
     * 同时记录 [remoteRev]（供后续 PUT 乐观锁）与 [savedSnapshot]（供无变化跳过）；失败时保持编辑器不变并打日志。
     */
    fun loadNote(noteId: String) {
        undoStack.clear()
        redoStack.clear()
        viewModelScope.launch {
            when (val result = notesRepository.getNote(noteId)) {
                is ApiResult.Success -> {
                    val note = result.data ?: return@launch
                    val title = note.title.orEmpty()
                    val body = bodyOf(note.content)
                    remoteRev = note.rev
                    savedSnapshot = TextSnapshot(title, body)
                    _uiState.value = CreateUiState(
                        editingNoteId = note.id,
                        updatedAt = note.updatedAt.toEpochMillisOrNull(),
                        title = title,
                        body = body,
                        selectedTags = emptyList(),
                        selectedFolder = null,
                        borderColor = ColorUtils.parseHexColor(note.borderColorHex),
                        availableFolders = availableFolders,
                        availableTags = availableTags,
                    )
                    // 拉附件，得到 fileId→签名 URL，供编辑器渲染 path 失效时兜底
                    fetchAttachments(note.id)
                }
                is ApiResult.BizError ->
                    AppLog.w(TAG) { "loadNote 业务错误 id=$noteId code=${result.code} traceId=${result.traceId}" }
                is ApiResult.NetworkError ->
                    AppLog.w(TAG) { "loadNote 网络错误 id=$noteId: ${result.message}" }
            }
        }
    }

    /** 从 App content JSON（约定 `{"body": <文档字符串>}`）抽取正文；非该结构或解析失败回退空串。 */
    private fun bodyOf(content: String): String =
        runCatching { JSONObject(content).optString("body", "") }.getOrDefault("")

    /** ISO-8601 → epoch 毫秒；空或解析失败回退 null。 */
    private fun String?.toEpochMillisOrNull(): Long? =
        this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    // ── 事件处理 ──────────────────────────────────────────────────────────────

    fun onEvent(event: CreateEvent) {
        when (event) {
            is CreateEvent.TitleChanged -> {
                updateText(newTitle = event.value, newBody = _uiState.value.body)
                requestSave()
            }

            is CreateEvent.ContentChanged -> {
                updateText(newTitle = _uiState.value.title, newBody = event.document)
                requestSave()
            }

            is CreateEvent.UndoEdit -> {
                if (undoStack.isEmpty()) return
                val current = TextSnapshot(_uiState.value.title, _uiState.value.body)
                redoStack.addLast(current)
                val prev = undoStack.removeLast()
                _uiState.update {
                    it.copy(
                        title = prev.title,
                        body = prev.body,
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = true,
                    )
                }
                requestSave()
            }

            is CreateEvent.RedoEdit -> {
                if (redoStack.isEmpty()) return
                val current = TextSnapshot(_uiState.value.title, _uiState.value.body)
                undoStack.addLast(current)
                val next = redoStack.removeLast()
                _uiState.update {
                    it.copy(
                        title = next.title,
                        body = next.body,
                        canUndo = true,
                        canRedo = redoStack.isNotEmpty(),
                    )
                }
                requestSave()
            }

            is CreateEvent.TagToggled -> {
                _uiState.update { state ->
                    val isSelected = state.selectedTags.any { it.id == event.tag.id }
                    if (isSelected) {
                        // 取消选中：仅从已选移除，列表顺序不变
                        state.copy(selectedTags = state.selectedTags.filterNot { it.id == event.tag.id })
                    } else {
                        // 新选中：可选列表顺序保持不变；只把最新选中放到 selectedTags 最前（供 meta 行显示）
                        state.copy(
                            selectedTags = listOf(event.tag) + state.selectedTags,
                        )
                    }
                }
                requestSave()
            }

            is CreateEvent.NewTagCreated -> {
                // 新建标签落库（出现在标签库中）；并选中给当前笔记
                val newTag = Tag(name = event.name)
                viewModelScope.launch { tagRepository.create(newTag.name, newTag.colorHex) }
                _uiState.update { state ->
                    state.copy(selectedTags = listOf(newTag) + state.selectedTags)
                }
                requestSave()
            }

            is CreateEvent.FolderSelected -> {
                _uiState.update { it.copy(selectedFolder = event.folder, showFolderPicker = false) }
                requestSave()
            }

            is CreateEvent.NewFolderCreated -> {
                // 新建文件夹落库（出现在文件夹库中）；并选中给当前笔记
                val newFolder = Folder(name = event.name)
                viewModelScope.launch { folderRepository.create(event.name, null) }
                _uiState.update { state ->
                    state.copy(
                        selectedFolder = newFolder,
                        showFolderPicker = false,
                    )
                }
                requestSave()
            }

            is CreateEvent.ShowTagPicker ->
                _uiState.update { it.copy(showTagPicker = true) }

            is CreateEvent.DismissTagPicker ->
                _uiState.update { it.copy(showTagPicker = false) }

            is CreateEvent.ShowFolderPicker ->
                _uiState.update { it.copy(showFolderPicker = true) }

            is CreateEvent.DismissFolderPicker ->
                _uiState.update { it.copy(showFolderPicker = false) }

            is CreateEvent.ShowColorPicker ->
                _uiState.update { it.copy(showColorPicker = true) }

            is CreateEvent.DismissColorPicker ->
                _uiState.update { it.copy(showColorPicker = false) }

            is CreateEvent.BorderColorSelected -> {
                _uiState.update { it.copy(borderColor = event.color, showColorPicker = false) }
                viewModelScope.launch { applyBorderColor(event.color?.toHex()) }
            }

            is CreateEvent.SaveNote -> {
                viewModelScope.launch {
                    saveNow()
                    _navigateBack.tryEmit(Unit)
                }
            }

            is CreateEvent.DeleteNote -> {
                val noteId = _uiState.value.editingNoteId
                viewModelScope.launch {
                    // 已保存过的笔记移入回收站（PATCH {trashed:true}，软删可恢复）；未保存的新笔记直接返回
                    if (noteId != null) {
                        when (val r = notesRepository.setTrashed(noteId, trashed = true)) {
                            is ApiResult.Success -> AppLog.i(TAG) { "moveToTrash 成功 id=$noteId" }
                            is ApiResult.BizError -> AppLog.w(TAG) { "moveToTrash 业务错误 id=$noteId code=${r.code} traceId=${r.traceId}" }
                            is ApiResult.NetworkError -> AppLog.w(TAG) { "moveToTrash 网络错误 id=$noteId: ${r.message}" }
                        }
                    }
                    _navigateBack.tryEmit(Unit)
                }
            }

            is CreateEvent.PermanentDeleteNote -> {
                val noteId = _uiState.value.editingNoteId
                viewModelScope.launch {
                    // 永久删除（DELETE，两步制：须已在回收站）；未保存的新笔记直接返回
                    if (noteId != null) {
                        when (val r = notesRepository.deleteNote(noteId)) {
                            is ApiResult.Success -> AppLog.i(TAG) { "permanentDelete 成功 id=$noteId" }
                            is ApiResult.BizError -> AppLog.w(TAG) { "permanentDelete 业务错误 id=$noteId code=${r.code} traceId=${r.traceId}" }
                            is ApiResult.NetworkError -> AppLog.w(TAG) { "permanentDelete 网络错误 id=$noteId: ${r.message}" }
                        }
                    }
                    _navigateBack.tryEmit(Unit)
                }
            }
        }
    }

    // ── 私有方法 ──────────────────────────────────────────────────────────────

    /** 任意内容变更 → 发一个保存信号（由防抖/封顶两条流统一去重限频后落盘）。 */
    private fun requestSave() {
        saveTrigger.tryEmit(Unit)
    }

    /** 立即落盘（退后台 / 离开页面等兜底；saveNow 幂等，内容未变会自动跳过）。 */
    fun flush() {
        viewModelScope.launch { saveNow() }
    }

    /**
     * 落盘到服务端：无 id → `POST /notes` 新建；有 id → `PUT /notes/{id}` 全量更新（latest-wins 乐观锁）。
     *
     * 由 [saveMutex] 串行化，避免防抖/封顶两条流并发写、乱序，也保证「新建的首个请求先拿到服务端 id」
     * 后，后续请求走更新而非重复新建。仅同步 title + 正文（服务端 content 约定 `{"body": …}`）；
     * 边框色（另有 border-color 端点）、标签/文件夹（本地概念）不经此路径。
     */
    private suspend fun saveNow() = saveMutex.withLock {
        val state = _uiState.value
        // 标题为空、且正文文档无文字也无图片时，视为空笔记不保存
        if (state.title.isBlank() && NoteDocument.previewText(state.body).isBlank()) {
            return@withLock
        }

        val current = TextSnapshot(state.title, state.body)
        // 已保存过且内容无变化 → 跳过，避免重复 POST/PUT
        if (state.editingNoteId != null && savedSnapshot == current) {
            return@withLock
        }

        val id = state.editingNoteId
        _uiState.update { it.copy(isSaving = true) }
        try {
            if (id == null) {
                // 新建：POST /notes
                when (val r = notesRepository.createNote(title = state.title, body = state.body)) {
                    is ApiResult.Success -> r.data?.let { note ->
                        remoteRev = note.rev
                        savedSnapshot = current
                        _uiState.update {
                            it.copy(editingNoteId = note.id, updatedAt = note.updatedAt.toEpochMillisOrNull())
                        }
                        AppLog.i(TAG) { "createNote 成功 id=${note.id} rev=${note.rev}" }
                    }
                    is ApiResult.BizError -> {
                        AppLog.w(TAG) { "createNote 业务错误 code=${r.code} traceId=${r.traceId} msg=${r.message}" }
                        _saveError.tryEmit(r.message ?: "Save failed (${r.code})")
                    }
                    is ApiResult.NetworkError -> {
                        AppLog.w(TAG) { "createNote 网络错误: ${r.message}" }
                        _saveError.tryEmit("Network error, note not saved")
                    }
                }
            } else {
                // 更新：PUT /notes/{id}（latest-wins 乐观锁，带上手上的 rev）
                when (val r = notesRepository.updateNote(
                    id = id, rev = remoteRev ?: 0L, title = state.title, body = state.body,
                )) {
                    is ApiResult.Success -> {
                        val outcome = r.data
                        outcome?.note?.rev?.let { remoteRev = it }
                        savedSnapshot = current
                        outcome?.note?.updatedAt?.toEpochMillisOrNull()?.let { ua ->
                            _uiState.update { it.copy(updatedAt = ua) }
                        }
                        if (outcome?.applied == false) {
                            AppLog.w(TAG) { "updateNote 落后未生效 id=$id serverRev=${outcome.note?.rev}（latest-wins，已同步服务端 rev）" }
                        } else {
                            AppLog.i(TAG) { "updateNote 成功 id=$id newRev=${outcome?.note?.rev}" }
                        }
                    }
                    is ApiResult.BizError -> {
                        AppLog.w(TAG) { "updateNote 业务错误 id=$id code=${r.code} traceId=${r.traceId} msg=${r.message}" }
                        _saveError.tryEmit(r.message ?: "Save failed (${r.code})")
                    }
                    is ApiResult.NetworkError -> {
                        AppLog.w(TAG) { "updateNote 网络错误 id=$id: ${r.message}" }
                        _saveError.tryEmit("Network error, note not saved")
                    }
                }
            }
            // 正文落盘后做附件对账（笔记已有 id 时）：正文里已上传的图片挂上、移除的摘掉
            _uiState.value.editingNoteId?.let { noteId -> reconcileAttachments(noteId, state.body) }
        } finally {
            _uiState.update { it.copy(isSaving = false) }
        }
        Unit
    }

    /**
     * 设置/清除边框色到服务端（`PATCH /notes/{id}/border-color`）。边框色不走 title/body 的 PUT，
     * 是独立端点。新笔记（尚无服务端 id）先 [saveNow] 创建拿到 id 再改色；改色会返回新 rev，
     * 同步到 [remoteRev] 以免后续 PUT 因版本落后被判过期。
     */
    private suspend fun applyBorderColor(hex: String?) {
        // 先确保有服务端 id（saveNow 自身加锁，故在获取 saveMutex 前调用，避免 Mutex 非重入死锁）
        if (_uiState.value.editingNoteId == null) saveNow()
        val id = _uiState.value.editingNoteId ?: return
        saveMutex.withLock {
            when (val r = notesRepository.setBorderColor(id, hex)) {
                is ApiResult.Success -> {
                    r.data?.rev?.let { remoteRev = it }
                    AppLog.i(TAG) { "setBorderColor 成功 id=$id hex=$hex rev=${r.data?.rev}" }
                }
                is ApiResult.BizError -> {
                    AppLog.w(TAG) { "setBorderColor 业务错误 id=$id code=${r.code} traceId=${r.traceId} msg=${r.message}" }
                    _saveError.tryEmit(r.message ?: "Failed to set colour (${r.code})")
                }
                is ApiResult.NetworkError -> {
                    AppLog.w(TAG) { "setBorderColor 网络错误 id=$id: ${r.message}" }
                    _saveError.tryEmit("Network error, colour not saved")
                }
            }
            Unit
        }
    }

    // ── 图片上传 / 附件 ──────────────────────────────────────────────────────────

    /**
     * 上传一张图片到服务端（presign→直传→confirm），返回 `fileId`。并发受 [uploadSemaphore] 限流。
     * [contentType] 由调用方按文件类型给出（image/png 或 image/jpeg）。挂到笔记发生在保存时的附件对账。
     */
    suspend fun uploadImage(path: String, contentType: String): Result<String> =
        uploadSemaphore.withPermit { filesRepository.uploadFile(File(path), contentType) }

    /** 打开已有笔记时拉附件：初始化已挂载集合与 fileId→URL 映射（供编辑器渲染兜底）。 */
    private suspend fun fetchAttachments(noteId: String) {
        when (val r = attachmentsRepository.list(noteId)) {
            is ApiResult.Success -> {
                val items = r.data.orEmpty()
                attachedFileIds.clear()
                attachedFileIds.addAll(items.map { it.fileId })
                _attachmentUrls.value = items
                    .mapNotNull { a -> a.downloadUrl?.let { a.fileId to it } }
                    .toMap()
            }
            is ApiResult.BizError -> AppLog.w(TAG) { "fetchAttachments 业务错误 noteId=$noteId code=${r.code}" }
            is ApiResult.NetworkError -> AppLog.w(TAG) { "fetchAttachments 网络错误 noteId=$noteId: ${r.message}" }
        }
    }

    /**
     * 附件对账：把正文里的图片 fileId 集合与已挂载集合求差，多的 attach、少的 detach（幂等）。
     * 在 saveNow 内、笔记已有 id 后调用；失败仅记日志、不阻塞正文保存（下次保存重试）。
     */
    private suspend fun reconcileAttachments(noteId: String, body: String) {
        val wanted = imageFileIdsOf(body)
        (wanted - attachedFileIds).forEach { fileId ->
            when (val r = attachmentsRepository.attach(noteId, fileId)) {
                is ApiResult.Success -> attachedFileIds.add(fileId)
                is ApiResult.BizError -> AppLog.w(TAG) { "attach 失败 fileId=$fileId code=${r.code} traceId=${r.traceId}" }
                is ApiResult.NetworkError -> AppLog.w(TAG) { "attach 网络错误 fileId=$fileId: ${r.message}" }
            }
        }
        (attachedFileIds - wanted).forEach { fileId ->
            when (val r = attachmentsRepository.detach(noteId, fileId)) {
                is ApiResult.Success -> attachedFileIds.remove(fileId)
                is ApiResult.BizError -> AppLog.w(TAG) { "detach 失败 fileId=$fileId code=${r.code} traceId=${r.traceId}" }
                is ApiResult.NetworkError -> AppLog.w(TAG) { "detach 网络错误 fileId=$fileId: ${r.message}" }
            }
        }
    }

    /** 从 content 文档 JSON（`{"blocks":[...]}`）提取所有图片块已上传的 fileId。 */
    private fun imageFileIdsOf(body: String): Set<String> = runCatching {
        val blocks = JSONObject(body).optJSONArray("blocks") ?: return emptySet()
        buildSet {
            for (i in 0 until blocks.length()) {
                val obj = blocks.optJSONObject(i) ?: continue
                if (obj.optString("type") == "image") {
                    obj.optString("fileId").takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }
    }.getOrDefault(emptySet())

    private fun updateText(newTitle: String, newBody: String) {
        val current = TextSnapshot(_uiState.value.title, _uiState.value.body)
        if (current.title == newTitle && current.body == newBody) return
        undoStack.addLast(current)
        if (undoStack.size > AppConfig.Editor.MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        _uiState.update {
            it.copy(title = newTitle, body = newBody, canUndo = true, canRedo = false)
        }
    }

    private companion object {
        const val TAG = "CreateVM"
    }
}
