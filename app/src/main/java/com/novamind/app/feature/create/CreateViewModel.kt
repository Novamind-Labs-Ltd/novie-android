package com.novamind.app.feature.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.data.NoteRepository
import com.novamind.app.feature.create.model.Note
import com.novamind.app.feature.create.model.Tag
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

class CreateViewModel : ViewModel() {

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
        val note = NoteRepository.notes.value.find { it.id == noteId } ?: return
        _uiState.value = CreateUiState(
            editingNoteId = note.id,
            title = note.title,
            body = note.body,
            selectedTags = note.tags,
            selectedFolder = note.folder,
        )
    }

    // ── 事件处理 ──────────────────────────────────────────────────────────────

    fun onEvent(event: CreateEvent) {
        when (event) {
            is CreateEvent.TitleChanged -> {
                updateText(newTitle = event.value, newBody = _uiState.value.body)
                scheduleAutoSave()
            }

            is CreateEvent.BodyChanged -> {
                updateText(newTitle = _uiState.value.title, newBody = event.value)
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
                    val selected = state.selectedTags.toMutableList()
                    if (selected.any { it.id == event.tag.id }) selected.removeAll { it.id == event.tag.id }
                    else selected.add(event.tag)
                    state.copy(selectedTags = selected)
                }
                saveNow()
            }

            is CreateEvent.NewTagCreated -> {
                val newTag = Tag(name = event.name)
                _uiState.update { state ->
                    state.copy(
                        availableTags = state.availableTags + newTag,
                        selectedTags = state.selectedTags + newTag,
                    )
                }
                saveNow()
            }

            is CreateEvent.FolderSelected -> {
                _uiState.update { it.copy(selectedFolder = event.folder, showFolderPicker = false) }
                saveNow()
            }

            is CreateEvent.ShowTagPicker ->
                _uiState.update { it.copy(showTagPicker = true) }

            is CreateEvent.DismissTagPicker ->
                _uiState.update { it.copy(showTagPicker = false) }

            is CreateEvent.ShowFolderPicker ->
                _uiState.update { it.copy(showFolderPicker = true) }

            is CreateEvent.DismissFolderPicker ->
                _uiState.update { it.copy(showFolderPicker = false) }

            // 返回时立即保存并导航
            is CreateEvent.SaveNote -> {
                autoSaveJob?.cancel()
                saveNow()
                _navigateBack.tryEmit(Unit)
            }
        }
    }

    // ── 私有方法 ──────────────────────────────────────────────────────────────

    /** 600ms 防抖自动保存 */
    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DELAY_MS)
            saveNow()
        }
    }

    /** 立即持久化到 Repository（内容为空则跳过） */
    private fun saveNow() {
        val state = _uiState.value
        if (state.title.isBlank() && state.body.isBlank()) return
        NoteRepository.addOrUpdate(
            Note(
                id = state.editingNoteId ?: UUID.randomUUID().toString().also { newId ->
                    _uiState.update { it.copy(editingNoteId = newId) }
                },
                title = state.title.ifBlank { "Untitled" },
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
