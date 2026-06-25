package com.novamind.app.feature.create

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.NovieApplication
import com.novamind.app.common.config.AppConfig
import com.novamind.app.data.NoteRepository
import com.novamind.app.feature.create.editor.NoteDocument
import com.novamind.app.feature.create.folder.Folder
import com.novamind.app.feature.create.model.Note
import com.novamind.app.feature.create.tag.Tag
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

private data class TextSnapshot(val title: String, val body: String)

@OptIn(FlowPreview::class)
class CreateViewModel(application: Application) : AndroidViewModel(application) {

    private val noteRepository: NoteRepository = (application as NovieApplication).noteRepository

    private val _uiState = MutableStateFlow(CreateUiState())
    val uiState = _uiState.asStateFlow()

    private val _navigateBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateBack = _navigateBack.asSharedFlow()

    private val undoStack = ArrayDeque<TextSnapshot>()
    private val redoStack = ArrayDeque<TextSnapshot>()

    // 保存触发器：所有变更只发一个信号，由下面两条流去重/限频后落盘。
    private val saveTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    // 串行化保存，避免防抖保存与封顶保存并发写、乱序。
    private val saveMutex = Mutex()

    // 已落库的笔记快照：用于判断内容是否真的变化（未变则不写库、不更新 updatedAt），
    // 并保留原始 createdAt。
    private var persistedNote: Note? = null

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
    }

    // ── 初始化 / 重置 ─────────────────────────────────────────────────────────

    fun reset() {
        undoStack.clear()
        redoStack.clear()
        persistedNote = null
        _uiState.value = CreateUiState()
    }

    fun loadNote(noteId: String) {
        undoStack.clear()
        redoStack.clear()
        viewModelScope.launch {
            val note = noteRepository.getNoteById(noteId) ?: return@launch
            persistedNote = note
            _uiState.value = CreateUiState(
                editingNoteId = note.id,
                updatedAt = note.updatedAt,
                title = note.title,
                body = note.body,
                selectedTags = note.tags,
                selectedFolder = note.folder,
                borderColorHex = note.borderColorHex,
            )
        }
    }

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
                val newTag = Tag(name = event.name)
                _uiState.update { state ->
                    // 新建即选中：放到可选列表最前面
                    state.copy(
                        availableTags = listOf(newTag) + state.availableTags,
                        selectedTags = listOf(newTag) + state.selectedTags,
                    )
                }
                requestSave()
            }

            is CreateEvent.FolderSelected -> {
                _uiState.update { it.copy(selectedFolder = event.folder, showFolderPicker = false) }
                requestSave()
            }

            is CreateEvent.NewFolderCreated -> {
                val newFolder = Folder(name = event.name)
                _uiState.update { state ->
                    state.copy(
                        availableFolders = state.availableFolders + newFolder,
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
                _uiState.update { it.copy(borderColorHex = event.hex, showColorPicker = false) }
                requestSave()
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
                    // 已保存过的笔记才需要删库；未保存的新笔记直接返回
                    if (noteId != null) noteRepository.delete(noteId)
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

    private suspend fun saveNow() = saveMutex.withLock {
        val state = _uiState.value
        // 标题为空、且正文文档无文字也无图片时，视为空笔记不保存
        if (state.title.isBlank() && NoteDocument.previewText(state.body).isBlank()) return

        // 内容相对已落库快照没有任何变化 → 不写库、不更新 updatedAt（仅查看后返回不应刷新时间）
        val saved = persistedNote
        if (saved != null &&
            saved.title == state.title &&
            saved.body == state.body &&
            saved.tags == state.selectedTags &&
            saved.folder == state.selectedFolder &&
            saved.borderColorHex == state.borderColorHex
        ) {
            return
        }

        val noteId = state.editingNoteId ?: UUID.randomUUID().toString().also { newId ->
            _uiState.update { it.copy(editingNoteId = newId) }
        }
        val note = Note(
            id = noteId,
            title = state.title,   // 允许为空：列表卡片会用正文内容兜底显示
            body = state.body,
            tags = state.selectedTags,
            folder = state.selectedFolder,
            borderColorHex = state.borderColorHex,
            createdAt = saved?.createdAt ?: System.currentTimeMillis(), // 保留原始创建时间
            updatedAt = System.currentTimeMillis(),                     // 仅在内容确有变化时刷新
        )
        noteRepository.addOrUpdate(note)
        persistedNote = note
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
}
