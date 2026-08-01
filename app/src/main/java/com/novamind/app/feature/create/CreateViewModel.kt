package com.novamind.app.feature.create

import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.common.config.AppConfig
import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.TranscriptSegmentDto
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.fold
import com.novamind.app.data.AttachmentsRepository
import com.novamind.app.data.AudioUploadRepository
import com.novamind.app.data.FilesRepository
import com.novamind.app.data.FoldersRepository
import com.novamind.app.data.RemoteNoteRepository
import com.novamind.app.data.TagRepository
import com.novamind.app.data.TranscriptionRepository
import com.novamind.app.feature.create.editor.NoteDocument
import com.novamind.app.feature.create.folder.Folder
import com.novamind.app.feature.create.model.TextSnapshot
import com.novamind.app.feature.create.model.TranscriptionInsert
import com.novamind.app.feature.create.data.PolishRepository
import com.novamind.app.common.net.PolishRequestDto
import com.novamind.app.feature.create.tag.Tag
import com.novamind.app.util.ColorUtils
import com.novamind.app.util.ColorUtils.toHex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject
import java.io.File
import java.time.Instant
import javax.inject.Inject

/**
 * 从服务端 Note content 提取编辑器正文。
 *
 * 普通笔记使用 `{"body":"{\"blocks\":[...]}"}` 信封；Ask Novie 保存的笔记则可能直接
 * 使用 `{"blocks":[...]}` doc-tree。两种格式都转换为编辑器统一消费的 doc-tree 字符串。
 */
internal fun noteBodyOf(content: String): String = runCatching {
    val root = Json.parseToJsonElement(content).jsonObject
    when {
        "body" in root -> when (val body = root["body"]) {
            is JsonPrimitive -> body.content
            is JsonObject -> body.toString()
            else -> ""
        }
        root["blocks"] is JsonArray -> root.toString()
        else -> ""
    }
}.getOrDefault("")

/**
 * 「新建 / 编辑笔记」页的 ViewModel（MVVM 单一状态源）：持有 [CreateUiState]，承接编辑器的
 * 标题 / 正文 / 标签 / 文件夹 / 边框色变更。主要职责：
 * - **自动保存**：变更发 [saveTrigger]，经防抖 + 封顶两条流限频，由 [saveNow] 串行落盘
 *   （无 id 走 POST、有 id 走 PUT，latest-wins 乐观锁）；
 * - **撤销 / 重做**：本地 [undoStack] / [redoStack] 维护标题+正文快照；
 * - **附件**：图片上传（[uploadImage]）+ 保存时按正文对账挂载/摘除（[reconcileAttachments]）；
 * - **源录音转写**：录音上传（[uploadRecording]）→ 轮询结果（[startTranscriptionPolling]）→ 追加进正文。
 *
 * 一次性事件（导航返回 / 保存失败 / 录音上传成功 / 转写就绪）用 [MutableSharedFlow] 暴露，避免重组重复触发。
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class CreateViewModel @Inject constructor(
    private val notesRepository: RemoteNoteRepository,
    private val foldersRepository: FoldersRepository,
    private val tagRepository: TagRepository,
    private val filesRepository: FilesRepository,
    private val attachmentsRepository: AttachmentsRepository,
    private val audioUploadRepository: AudioUploadRepository,
    private val transcriptionRepository: TranscriptionRepository,
) : ViewModel() {

    // ── 对外状态与一次性事件 ─────────────────────────────────────────────────────

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

    // 源录音上传成功的一次性事件（供 UI 关闭录音面板）。
    private val _recordingUploaded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val recordingUploaded = _recordingUploaded.asSharedFlow()

    // 转写结果就绪的一次性事件：携带渲染好的 HTML + 完成 ack，供 UI 追加进正文（§9）。
    // UI 追加并回流 body 后 complete(ack)，VM 据此确定「UI 已更新完」再保存，保证顺序（见 startTranscriptionPolling）。
    private val _transcriptionReady = MutableSharedFlow<TranscriptionInsert>(extraBufferCapacity = 1)
    val transcriptionReady = _transcriptionReady.asSharedFlow()

    // 图片上传并发限流（一次多选最多 5 张，限 AppConfig.Media.MAX_UPLOAD_CONCURRENCY 并发、其余排队）。
    private val uploadSemaphore = Semaphore(AppConfig.Media.MAX_UPLOAD_CONCURRENCY)

    // 已挂载到该笔记的附件 fileId 集合（loadNote 从服务端拉取初始化；saveNow 对账时增删）。
    private val attachedFileIds = mutableSetOf<String>()

    // 附件 fileId → 签名下载 URL（loadNote 后由 GET attachments 提供，供编辑器渲染兜底）。
    private val _attachmentUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val attachmentUrls: StateFlow<Map<String, String>> = _attachmentUrls.asStateFlow()

    // ── 内部记账（撤销栈 / 保存限频 / 乐观锁版本 / 在途协程） ──────────────────────

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

    // 进行中的源录音上传协程：再次录音或用户取消时中断上一次。
    private var audioUploadJob: Job? = null

    // 进行中的转写轮询协程：新一次录音上传时中断上一次。
    private var transcriptionJob: Job? = null

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
     * 打开已有笔记：**先拉转写**（GET /notes/{id}/transcription），据此决定何时拉详情：
     * - 无待处理任务 / FAILED → 直接拉笔记详情；
     * - PROCESSING → 先只显示 loading 轮询，**暂不拉详情**；转写 READY 落盘后再拉详情并追加。
     *
     * 详情加载逻辑抽到 [loadNoteDetail]，由 [startTranscriptionPolling] 在恰当时机调用。
     */
    fun loadNote(noteId: String) {
        undoStack.clear()
        redoStack.clear()
        startTranscriptionPolling(noteId, loadDetail = true)
    }

    /**
     * 拉取笔记详情（GET /notes/{id}）填充编辑器 + 附件。服务端 NoteView 不含本地标签/文件夹，故留空；
     * 正文按 `{"body": …}` 约定抽取；记录 [remoteRev] / [savedSnapshot]。失败保持编辑器不变并打日志。
     */
    private suspend fun loadNoteDetail(noteId: String) {
        notesRepository.getNote(noteId).fold(
            onSuccess = { note ->
                note ?: return@fold
                val title = note.title.orEmpty()
                val body = noteBodyOf(note.content)
                remoteRev = note.rev
                savedSnapshot = TextSnapshot(title, body)
                // 所属文件夹：优先用后端随 NoteView 返回的 folderName；缺名时回退本地已缓存的文件夹列表。
                // folderId 为空 = 未归档，selectedFolder 置 null（Meta 行显示「Unassigned」）。
                val folder = note.folderId?.let { fid ->
                    Folder(id = fid, name = note.folderName ?: availableFolders.firstOrNull { it.id == fid }?.name.orEmpty())
                }
                _uiState.value = CreateUiState(
                    editingNoteId = note.id,
                    updatedAt = note.updatedAt.toEpochMillisOrNull(),
                    title = title,
                    body = body,
                    selectedTags = emptyList(),
                    selectedFolder = folder,
                    borderColor = ColorUtils.parseHexColor(note.borderColorHex),
                    availableFolders = availableFolders,
                    availableTags = availableTags,
                )
                // 拉附件，得到 fileId→签名 URL，供编辑器渲染 path 失效时兜底
                fetchAttachments(note.id)
            },
            onFail = { logApiError("loadNote id=$noteId", it) },
        )
    }

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
                // 归属落库：PATCH /notes/{id} 的 folderId(null=移出未归档);新笔记先建档拿 id
                viewModelScope.launch { applyFolder(event.folder?.id) }
            }

            is CreateEvent.NewFolderCreated -> {
                // 新建文件夹到服务端（POST /folders），成功后刷新列表；并乐观选中给当前笔记
                _uiState.update { state ->
                    state.copy(
                        selectedFolder = Folder(name = event.name),
                        showFolderPicker = false,
                    )
                }
                viewModelScope.launch {
                    foldersRepository.createFolder(event.name).fold(
                        onSuccess = { f ->
                            // 用服务端返回的 id 校正选中项
                            f?.let { folder ->
                                _uiState.update { st ->
                                    if (st.selectedFolder?.name == folder.name) {
                                        st.copy(selectedFolder = Folder(id = folder.id, name = folder.name))
                                    } else st
                                }
                                // 归属落库：把当前笔记移入这个新建文件夹
                                applyFolder(folder.id)
                            }
                            loadFolders()
                        },
                        onFail = { logApiError("createFolder", it) },
                    )
                }
            }

            is CreateEvent.ShowTagPicker ->
                _uiState.update { it.copy(showTagPicker = true) }

            is CreateEvent.DismissTagPicker ->
                _uiState.update { it.copy(showTagPicker = false) }

            is CreateEvent.ShowFolderPicker -> {
                loadFolders()   // 打开选择器时刷新一次服务端文件夹
                _uiState.update { it.copy(showFolderPicker = true) }
            }

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
                        notesRepository.setTrashed(noteId, trashed = true).fold(
                            onSuccess = { AppLog.i(TAG) { "moveToTrash 成功 id=$noteId" } },
                            onFail = { logApiError("moveToTrash id=$noteId", it) },
                        )
                    }
                    _navigateBack.tryEmit(Unit)
                }
            }

            is CreateEvent.PermanentDeleteNote -> {
                val noteId = _uiState.value.editingNoteId
                viewModelScope.launch {
                    // 永久删除（DELETE，两步制：须已在回收站）；未保存的新笔记直接返回
                    if (noteId != null) {
                        notesRepository.deleteNote(noteId).fold(
                            onSuccess = { AppLog.i(TAG) { "permanentDelete 成功 id=$noteId" } },
                            onFail = { logApiError("permanentDelete id=$noteId", it) },
                        )
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

    /** 新建成功后回填服务端 id/rev 并记住已存快照（[saveNow] 与 [uploadRecording] 复用）。 */
    private fun markNoteCreated(id: String, rev: Long?, snapshot: TextSnapshot) {
        remoteRev = rev
        savedSnapshot = snapshot
        _uiState.update { it.copy(editingNoteId = id) }
    }

    /**
     * API 失败统一处理：记日志 + 发一次性 [saveError]（供 UI 弹 Toast）。[op] 供日志定位。
     * 业务错误优先用后端 message，缺省回退 `"$bizFallback (code)"`；网络错误用 [networkMsg]。
     */
    private fun notifyError(op: String, r: ApiResult<*>, bizFallback: String, networkMsg: String) {
        when (r) {
            is ApiResult.BizError -> {
                AppLog.w(TAG) { "$op 业务错误 code=${r.code} traceId=${r.traceId} msg=${r.message}" }
                _saveError.tryEmit(r.message ?: "$bizFallback (${r.code})")
            }
            is ApiResult.NetworkError -> {
                AppLog.w(TAG) { "$op 网络错误: ${r.message}" }
                _saveError.tryEmit(networkMsg)
            }
            is ApiResult.Success -> Unit
        }
    }

    /** 仅记录 API 错误日志（无用户提示），用于纯读 / best-effort 场景。 */
    private fun logApiError(op: String, r: ApiResult<*>) {
        when (r) {
            is ApiResult.BizError -> AppLog.w(TAG) { "$op 业务错误 code=${r.code} traceId=${r.traceId}" }
            is ApiResult.NetworkError -> AppLog.w(TAG) { "$op 网络错误: ${r.message}" }
            is ApiResult.Success -> Unit
        }
    }

    /**
     * 落盘到服务端：无 id → `POST /notes` 新建；有 id → `PUT /notes/{id}` 全量更新（latest-wins 乐观锁）。
     *
     * 由 [saveMutex] 串行化，避免防抖/封顶两条流并发写、乱序，也保证「新建的首个请求先拿到服务端 id」
     * 后，后续请求走更新而非重复新建。仅同步 title + 正文（服务端 content 约定 `{"body": …}`）；
     * 边框色（另有 border-color 端点）、标签/文件夹（本地概念）不经此路径。
     *
     * @return 正文是否已就绪落盘：`true`=保存成功 / 空笔记或无变化的无需保存；
     *   `false`=保存失败或 latest-wins 未生效（供转写 READY 流据此决定是否 consume）。
     */
    private suspend fun saveNow(): Boolean = saveMutex.withLock {
        val state = _uiState.value
        // 标题为空、且正文文档无文字也无图片时，视为空笔记不保存（无内容可保存，视为已就绪）
        if (state.title.isBlank() && NoteDocument.previewText(state.body).isBlank()) {
            return@withLock true
        }

        val current = TextSnapshot(state.title, state.body)
        // 已保存过且内容无变化 → 跳过，避免重复 POST/PUT（视为已就绪）
        if (state.editingNoteId != null && savedSnapshot == current) {
            return@withLock true
        }

        val id = state.editingNoteId
        _uiState.update { it.copy(isSaving = true) }
        var ok = false
        try {
            if (id == null) {
                // 新建：POST /notes
                notesRepository.createNote(title = state.title, body = state.body).fold(
                    onSuccess = { data ->
                        data?.let { note ->
                            markNoteCreated(note.id, note.rev, current)
                            _uiState.update { it.copy(updatedAt = note.updatedAt.toEpochMillisOrNull()) }
                            AppLog.i(TAG) { "createNote 成功 id=${note.id} rev=${note.rev}" }
                            ok = true
                        }
                    },
                    onFail = { notifyError("createNote", it, "Save failed", "Network error, note not saved") },
                )
            } else {
                // 更新：PUT /notes/{id}（latest-wins 乐观锁，带上手上的 rev）
                notesRepository.updateNote(
                    id = id, rev = remoteRev ?: 0L, title = state.title, body = state.body,
                ).fold(
                    onSuccess = { outcome ->
                        outcome?.note?.rev?.let { remoteRev = it }
                        outcome?.note?.updatedAt?.toEpochMillisOrNull()?.let { ua ->
                            _uiState.update { it.copy(updatedAt = ua) }
                        }
                        if (outcome?.applied == true) {
                            // 只有服务端确认本次 latest-wins 更新生效，才能把本地内容标记为已保存。
                            // 冲突时保留旧快照，使后续自动保存/离页保存继续重试当前本地内容。
                            savedSnapshot = current
                            AppLog.i(TAG) { "updateNote 成功 id=$id newRev=${outcome.note?.rev}" }
                            ok = true
                        } else {
                            AppLog.w(TAG) { "updateNote 落后未生效 id=$id serverRev=${outcome?.note?.rev}（latest-wins，已同步服务端 rev）" }
                        }
                    },
                    onFail = { notifyError("updateNote id=$id", it, "Save failed", "Network error, note not saved") },
                )
            }
            // 仅在正文确实落盘后做附件对账，避免保存失败/版本冲突时提前挂载或摘除附件，
            // 导致服务端正文与附件关系不一致。
            if (ok) {
                _uiState.value.editingNoteId?.let { noteId -> reconcileAttachments(noteId, state.body) }
            }
        } finally {
            _uiState.update { it.copy(isSaving = false) }
        }
        ok
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
            notesRepository.setBorderColor(id, hex).fold(
                onSuccess = { note ->
                    note?.rev?.let { remoteRev = it }
                    AppLog.i(TAG) { "setBorderColor 成功 id=$id hex=$hex rev=${note?.rev}" }
                },
                onFail = { notifyError("setBorderColor id=$id", it, "Failed to set colour", "Network error, colour not saved") },
            )
        }
    }

    /**
     * 设置/清除笔记所属文件夹到服务端（`PATCH /notes/{id}` 的 folderId 分支）。与改色同理:
     * 新笔记（尚无服务端 id）先 [saveNow] 建档拿 id 再移动；移动返回新 rev，同步到 [remoteRev]
     * 以免后续 PUT 因版本落后被判过期。[folderId]=null 表示移出到未归档。
     */
    private suspend fun applyFolder(folderId: String?) {
        // 先确保有服务端 id（saveNow 自身加锁，故在获取 saveMutex 前调用，避免非重入死锁）
        if (_uiState.value.editingNoteId == null) saveNow()
        val id = _uiState.value.editingNoteId ?: return
        saveMutex.withLock {
            notesRepository.setFolder(id, folderId).fold(
                onSuccess = { note ->
                    note?.rev?.let { remoteRev = it }
                    AppLog.i(TAG) { "setFolder 成功 id=$id folderId=$folderId rev=${note?.rev}" }
                },
                onFail = { notifyError("setFolder id=$id", it, "Failed to move folder", "Network error, folder not saved") },
            )
        }
    }

    /**
     * 录音发送后上传为笔记源录音（§7 断点续传：initiate→分片直传→complete）。
     * 先确保有服务端 note id（源录音须挂到笔记；空笔记也强制创建以承载），再走 [AudioUploadRepository]。
     * 结果三态：成功记 jobId（转写票据）；失败经 [saveError] 提示。
     */
    fun uploadRecording(path: String, durationMs: Long) {
        audioUploadJob?.cancel()
        transcriptionJob?.cancel()   // 新一次录音：中断上一条的转写轮询
        _uiState.update { it.copy(isTranscribing = false) }
        audioUploadJob = viewModelScope.launch {
            if (_uiState.value.editingNoteId == null) saveNow()
            var noteId = _uiState.value.editingNoteId
            if (noteId == null) {
                // 仅录音的空笔记：强制创建一条以承载源录音
                val s = _uiState.value
                notesRepository.createNote(s.title, s.body).fold(
                    onSuccess = { data ->
                        data?.let { note ->
                            markNoteCreated(note.id, note.rev, TextSnapshot(s.title, s.body))
                            noteId = note.id
                        }
                    },
                    onFail = { logApiError("uploadRecording 建笔记", it) },
                )
            }
            val id = noteId ?: run {
                AppLog.w(TAG) { "uploadRecording 无 noteId，跳过上传" }
                return@launch
            }
            // 进度条起始：清零并置上传中
            _uiState.update { it.copy(isUploadingAudio = true, audioUploadProgress = 0f) }
            var lastPct = -1
            try {
                audioUploadRepository.uploadNoteAudio(
                    noteId = id,
                    file = File(path),
                    contentType = AppConfig.Media.AUDIO_MIME,
                    durationMs = durationMs,
                    onProgress = { uploaded, total ->
                        val p = if (total > 0) (uploaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                        val pct = (p * 100).toInt()
                        if (pct != lastPct) {   // 节流：仅整百分比变化时更新，避免刷 UI
                            lastPct = pct
                            AppLog.i(TAG) { "uploadRecording 进度 noteId=$id $pct% ($uploaded/$total)" }
                            _uiState.update { it.copy(audioUploadProgress = p) }
                        }
                    },
                ).fold(
                    onSuccess = { jobId ->
                        AppLog.i(TAG) { "uploadRecording 成功 noteId=$id jobId=$jobId" }
                        _recordingUploaded.tryEmit(Unit)   // 通知 UI：上传成功，关闭录音面板
                        // 上传成功 → 轮询转写结果（§9）。详情已在编辑器，loadDetail=false 不重拉（避免覆盖编辑）。
                        startTranscriptionPolling(id, loadDetail = false)
                    },
                    onFail = { notifyError("uploadRecording noteId=$id", it, "Audio upload failed", "Network error, audio not uploaded") },
                )
            } finally {
                _uiState.update { it.copy(isUploadingAudio = false, audioUploadProgress = 0f) }
            }
        }
    }

    /** 取消进行中的源录音上传（用户点进度条上的 ×）。分片为断点续传，中断即停；未 complete 的不会挂到笔记。 */
    fun cancelAudioUpload() {
        audioUploadJob?.cancel()
        audioUploadJob = null
        _uiState.update { it.copy(isUploadingAudio = false, audioUploadProgress = 0f) }
    }

    /**
     * 离开笔记页时停止转写轮询：取消在途的 [transcriptionJob]，不再调用 /transcription。
     * 取消会走轮询的 finally 复位 [CreateUiState.isTranscribing]（隐藏 loading）。
     * 重新进入笔记会由 [startTranscriptionPolling] 按服务端状态（READY 填充 / PROCESSING 续轮询）恢复。
     */
    fun stopTranscriptionPolling() {
        transcriptionJob?.cancel()
        transcriptionJob = null
    }

    /**
     * 转写结果同步 + 轮询（§9）。进页（[loadDetail]=true）与录音上传成功（[loadDetail]=false）统一走这里。
     * 取**最新未消费**（kind=X）任务；首轮**立即**拉取，之后每隔 [AppConfig.Transcription.POLL_INTERVAL_MS] 一次：
     * - `READY`：按段追加正文 → 保存成功后才 consume（三步串行）；追加前确保详情已加载。
     * - `FAILED`：拉取详情并展示页内失败提示条，不弹 Toast。
     * - `PROCESSING`：标记 [CreateUiState.isTranscribing]（loading）继续轮询；此期间**暂不拉详情**。
     * - **无待处理任务**：结束，不空转、不显示 loading。
     * - 未确认 PROCESSING 前遇网络/业务错误即结束，避免每次进页空转。
     *
     * 详情加载（[loadNoteDetail]）时机：**打开笔记**（[loadDetail]=true）在「无任务 / READY / FAILED / 拉取失败」
     * 时才拉——即「有待处理任务先不拉详情」；**录音上传后**（[loadDetail]=false）详情已在编辑器，不重拉以免覆盖编辑。
     */
    private fun startTranscriptionPolling(noteId: String, loadDetail: Boolean) {
        transcriptionJob?.cancel()
        // 每次进入详情页或开始新一轮转写时先清除上一轮失败态；首轮接口结果会重新确认。
        _uiState.update { it.copy(hasTranscriptionFailed = false) }
        transcriptionJob = viewModelScope.launch {
            var confirmed = false            // 是否已确认存在进行中的转写（据此显示 loading、容忍网络抖动）
            var detailLoaded = !loadDetail   // 上传路径：详情已在编辑器，视为已加载、不再拉
            suspend fun ensureDetail() { if (!detailLoaded) { loadNoteDetail(noteId); detailLoaded = true } }
            // 打开笔记：拉转写状态 + 拉详情期间显示 loading（loadNoteDetail 加载完成会重置 UiState、自然关闭）。
            if (loadDetail) _uiState.update { it.copy(isLoading = true) }
            try {
                var first = true
                while (isActive) {
                    if (!first) delay(AppConfig.Transcription.POLL_INTERVAL_MS)
                    first = false
                    val tasks = when (val r = transcriptionRepository.list(noteId)) {
                        is ApiResult.Success -> r.data.orEmpty()
                        is ApiResult.BizError -> {   // 40401 等：终态，仍需展示笔记 → 补拉详情后停止
                            AppLog.w(TAG) { "transcription 业务错误 noteId=$noteId code=${r.code}，停止轮询" }
                            ensureDetail(); break
                        }
                        is ApiResult.NetworkError -> {   // 已确认在处理才重试；未确认前补拉详情后停止，不空转
                            AppLog.w(TAG) { "transcription 网络错误 noteId=$noteId: ${r.message}" }
                            if (confirmed) continue else { ensureDetail(); break }
                        }
                    }
                    // 取最新（末尾）未消费任务（kind=X）；无待处理 → 拉详情并结束
                    val task = tasks.lastOrNull { !it.consumed && it.kind == "X" }
                    if (task == null) { ensureDetail(); break }
                    when (task.presentationState) {
                        "READY" -> {
                            // 追加前确保详情已加载（PROCESSING 期间未拉时在此补拉），编辑器就绪后再追加
                            ensureDetail()
                            val text = formatSegments(task.resultSegments)
                            AppLog.i(TAG) { "transcription READY noteId=$noteId jobId=${task.jobId} len=${text.length}" }
                            // 三步严格串行，保障「文本先落盘再消费」：
                            // 1) 更新 UI：把转写文本追加进正文并显示；等 UI 明确回 ack（追加+回流 body 完成）再继续。
                            if (text.isNotBlank()) {
                                val ack = CompletableDeferred<Unit>()
                                _transcriptionReady.tryEmit(TranscriptionInsert(text, ack))
                                val done = withTimeoutOrNull(APPEND_SYNC_TIMEOUT_MS) { ack.await() } != null
                                if (!done) {
                                    // UI 未确认追加完成（未订阅/超时）→ 不保存也不 consume，避免消费后丢文本；下次进页重试。
                                    AppLog.w(TAG) { "transcription READY 追加未完成，暂不保存/consume noteId=$noteId jobId=${task.jobId}" }
                                    break
                                }
                            }
                            // 2) 保存追加后的正文。3) 仅保存成功才 consume；未存住（保存失败 / latest-wins 未生效）
                            //    → 不 consume，下次进页重试。
                            if (saveNow()) {
                                transcriptionRepository.consume(noteId, task.jobId)   // best-effort
                            } else {
                                AppLog.w(TAG) { "transcription READY 保存未成功，暂不 consume noteId=$noteId jobId=${task.jobId}" }
                            }
                            break
                        }
                        "FAILED" -> {
                            AppLog.w(TAG) { "transcription FAILED noteId=$noteId jobId=${task.jobId}" }
                            ensureDetail()
                            _uiState.update { it.copy(hasTranscriptionFailed = true) }
                            break
                        }
                        else -> {   // PROCESSING：确认在处理 → 显示 loading（暂不拉详情）并继续轮询
                            if (!confirmed) {
                                confirmed = true
                                _uiState.update { it.copy(isTranscribing = true) }
                            }
                        }
                    }
                }
            } finally {
                _uiState.update { it.copy(isTranscribing = false, isLoading = false) }
            }
        }
    }

    /**
     * 把转写分段拼成 **HTML**：**连续相同说话人的分段合并为一段**（`<p>` 一行，说话人前缀只出现一次），
     * 段内多条文本以空格拼接；有说话人时前缀 **加粗 + 配色** 的 Speaker
     * （`<b><span style="color:#..">Name</span></b>：文本`）凸显，无说话人只放文本。
     * 颜色从 [AppConfig.Editor.SPEAKER_PALETTE]（复用设计系统 Palette）取，按说话人名 hash 稳定映射——**同一 speaker 恒定同色**。
     * 结果经 [transcriptionReady] → `editor.appendHtml` 渲染进正文并随保存持久化（HTML）。
     */
    private fun formatSegments(segments: List<TranscriptSegmentDto>?): String {
        val list = segments.orEmpty().filter { it.text.isNotBlank() }
        if (list.isEmpty()) return ""
        // 合并连续相同说话人（含连续无说话人）的分段为一组，段内文本以空格拼接。
        val groups = mutableListOf<Pair<String?, StringBuilder>>()
        for (seg in list) {
            val speaker = seg.speaker?.trim()?.takeIf { it.isNotBlank() }
            val text = seg.text.trim()
            val last = groups.lastOrNull()
            if (last != null && last.first == speaker) {
                last.second.append(' ').append(text)
            } else {
                groups.add(speaker to StringBuilder(text))
            }
        }
        return groups.joinToString("") { (speaker, sb) ->
            val body = escapeHtml(sb.toString())
            if (speaker != null) {
                val color = speakerColor(speaker)
                "<p><b><span style=\"color:$color\">${escapeHtml(speaker)}</span></b>：$body</p>"
            } else {
                "<p>$body</p>"
            }
        }
    }

    /** 说话人 → 调色板颜色（`#RRGGBB`）：从 [AppConfig.Editor.SPEAKER_PALETTE] 按名字 hash 取模，同名恒定同色。 */
    private fun speakerColor(speaker: String): String {
        val palette = AppConfig.Editor.SPEAKER_PALETTE
        val idx = (speaker.hashCode() % palette.size + palette.size) % palette.size
        return "#%06X".format(0xFFFFFF and palette[idx].toArgb())
    }

    /** 转义 HTML 特殊字符，避免转写文本 / 说话人名破坏 HTML 结构。 */
    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // ── 图片上传 / 附件 ──────────────────────────────────────────────────────────

    /**
     * 上传一张图片到服务端（presign→直传→confirm），返回 `fileId`。并发受 [uploadSemaphore] 限流。
     * [contentType] 由调用方按文件类型给出（image/png 或 image/jpeg）。挂到笔记发生在保存时的附件对账。
     */
    suspend fun uploadImage(path: String, contentType: String): Result<String> =
        uploadSemaphore.withPermit { filesRepository.uploadFile(File(path), contentType) }

    /** 调用 agent 的无状态润色接口；编辑器快照和结果回填由 UI 层的 NoteEditorState 管理。 */
    suspend fun polish(request: PolishRequestDto): Result<String> = PolishRepository.polish(request)

    /** 打开已有笔记时拉附件：初始化已挂载集合与 fileId→URL 映射（供编辑器渲染兜底）。 */
    private suspend fun fetchAttachments(noteId: String) {
        attachmentsRepository.list(noteId).fold(
            onSuccess = { data ->
                val items = data.orEmpty()
                attachedFileIds.clear()
                attachedFileIds.addAll(items.map { it.fileId })
                _attachmentUrls.value = items
                    .mapNotNull { a -> a.downloadUrl?.let { a.fileId to it } }
                    .toMap()
            },
            onFail = { logApiError("fetchAttachments noteId=$noteId", it) },
        )
    }

    /**
     * 附件对账：把正文里的图片 fileId 集合与已挂载集合求差，多的 attach、少的 detach（幂等）。
     * 在 saveNow 内、笔记已有 id 后调用；失败仅记日志、不阻塞正文保存（下次保存重试）。
     */
    private suspend fun reconcileAttachments(noteId: String, body: String) {
        val wanted = imageFileIdsOf(body)
        (wanted - attachedFileIds).forEach { fileId ->
            attachmentsRepository.attach(noteId, fileId).fold(
                onSuccess = { attachedFileIds.add(fileId) },
                onFail = { logApiError("attach fileId=$fileId", it) },
            )
        }
        (attachedFileIds - wanted).forEach { fileId ->
            attachmentsRepository.detach(noteId, fileId).fold(
                onSuccess = { attachedFileIds.remove(fileId) },
                onFail = { logApiError("detach fileId=$fileId", it) },
            )
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

    /** 拉取服务端文件夹列表（GET /folders）填充选择器；失败保留现有列表并记日志。 */
    private fun loadFolders() {
        viewModelScope.launch {
            foldersRepository.listFolders(limit = AppConfig.Paging.FOLDERS_PAGE_SIZE).fold(
                onSuccess = { data ->
                    val folders = data?.items.orEmpty()
                        .sortedBy { it.sortOrder }
                        .map { Folder(id = it.id, name = it.name) }
                    availableFolders = folders
                    _uiState.update { it.copy(availableFolders = folders) }
                },
                onFail = { logApiError("loadFolders", it) },
            )
        }
    }

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

        /** 转写 READY 后等 UI 追加完成 ack 的最长等待，超时（如页面未订阅）则不保存/不 consume，下次进页重试。 */
        const val APPEND_SYNC_TIMEOUT_MS = 3_000L
    }
}
