package com.novamind.app.feature.create

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.NovieApplication
import com.novamind.app.data.NoteRepository
import com.novamind.app.feature.create.editor.NoteDocument
import com.novamind.app.feature.create.folder.Folder
import com.novamind.app.feature.create.model.Note
import com.novamind.app.feature.create.tag.Tag
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private data class TextSnapshot(val title: String, val body: String)
private const val MAX_HISTORY = 50
private const val AUTO_SAVE_DELAY_MS = 600L

class CreateViewModel(application: Application) : AndroidViewModel(application) {

    private val noteRepository: NoteRepository = (application as NovieApplication).noteRepository

    private val _uiState = MutableStateFlow(CreateUiState())
    val uiState = _uiState.asStateFlow()

    private val _navigateBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateBack = _navigateBack.asSharedFlow()

    private val undoStack = ArrayDeque<TextSnapshot>()
    private val redoStack = ArrayDeque<TextSnapshot>()
    private var autoSaveJob: Job? = null

    // ── 初始化 / 重置 ─────────────────────────────────────────────────────────

    fun reset() {
        autoSaveJob?.cancel()
        undoStack.clear()
        redoStack.clear()
        _uiState.value = CreateUiState()
    }

    fun loadNote(noteId: String) {
        autoSaveJob?.cancel()
        undoStack.clear()
        redoStack.clear()
        viewModelScope.launch {
            val note = noteRepository.getNoteById(noteId) ?: return@launch
            _uiState.value = CreateUiState(
                editingNoteId = note.id,
                updatedAt = note.updatedAt,
                title = note.title,
                body = note.body,
                selectedTags = note.tags,
                selectedFolder = note.folder,
            )
        }
    }

    // ── 事件处理 ──────────────────────────────────────────────────────────────

    fun onEvent(event: CreateEvent) {
        when (event) {
            is CreateEvent.TitleChanged -> {
                updateText(newTitle = event.value, newBody = _uiState.value.body)
                scheduleAutoSave()
            }

            is CreateEvent.ContentChanged -> {
                updateText(newTitle = _uiState.value.title, newBody = event.document)
                scheduleAutoSave()
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
                scheduleAutoSave()
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
                scheduleAutoSave()
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
                viewModelScope.launch { saveNow() }
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
                viewModelScope.launch { saveNow() }
            }

            is CreateEvent.FolderSelected -> {
                _uiState.update { it.copy(selectedFolder = event.folder, showFolderPicker = false) }
                viewModelScope.launch { saveNow() }
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
                viewModelScope.launch { saveNow() }
            }

            is CreateEvent.ShowTagPicker ->
                _uiState.update { it.copy(showTagPicker = true) }

            is CreateEvent.DismissTagPicker ->
                _uiState.update { it.copy(showTagPicker = false) }

            is CreateEvent.ShowFolderPicker ->
                _uiState.update { it.copy(showFolderPicker = true) }

            is CreateEvent.DismissFolderPicker ->
                _uiState.update { it.copy(showFolderPicker = false) }

            is CreateEvent.SaveNote -> {
                autoSaveJob?.cancel()
                viewModelScope.launch {
                    saveNow()
                    _navigateBack.tryEmit(Unit)
                }
            }

            is CreateEvent.DeleteNote -> {
                autoSaveJob?.cancel()
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

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DELAY_MS)
            saveNow()
        }
    }

    private suspend fun saveNow() {
        val state = _uiState.value
        // 标题为空、且正文文档无文字也无图片时，视为空笔记不保存
        if (state.title.isBlank() && NoteDocument.previewText(state.body).isBlank()) return
        val noteId = state.editingNoteId ?: UUID.randomUUID().toString().also { newId ->
            _uiState.update { it.copy(editingNoteId = newId) }
        }
        noteRepository.addOrUpdate(
            Note(
                id = noteId,
                title = state.title,   // 允许为空：列表卡片会用正文内容兜底显示
                body = state.body,
                tags = state.selectedTags,
                folder = state.selectedFolder,
            )
        )
    }

    private fun updateText(newTitle: String, newBody: String) {
        val current = TextSnapshot(_uiState.value.title, _uiState.value.body)
        if (current.title == newTitle && current.body == newBody) return
        undoStack.addLast(current)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        _uiState.update {
            it.copy(title = newTitle, body = newBody, canUndo = true, canRedo = false)
        }
    }
}
