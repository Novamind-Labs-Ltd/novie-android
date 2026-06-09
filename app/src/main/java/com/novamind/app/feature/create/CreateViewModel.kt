package com.novamind.app.feature.create

import androidx.lifecycle.ViewModel
import com.novamind.app.data.NoteRepository
import com.novamind.app.feature.create.model.Note
import com.novamind.app.feature.create.model.Tag
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 撤销/重做只针对文本内容（标题 + 正文），快照结构 */
private data class TextSnapshot(val title: String, val body: String)

private const val MAX_HISTORY = 50

class CreateViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CreateUiState())
    val uiState = _uiState.asStateFlow()

    private val _navigateBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateBack = _navigateBack.asSharedFlow()

    // 撤销栈：栈顶是最近一次变更前的状态
    private val undoStack = ArrayDeque<TextSnapshot>()
    // 重做栈：撤销后可重做
    private val redoStack = ArrayDeque<TextSnapshot>()

    // ── 公开方法 ──────────────────────────────────────────────────────────────

    fun reset() {
        undoStack.clear()
        redoStack.clear()
        _uiState.value = CreateUiState()
    }

    fun loadNote(noteId: String) {
        val note = NoteRepository.notes.value.find { it.id == noteId } ?: return
        undoStack.clear()
        redoStack.clear()
        _uiState.value = CreateUiState(
            editingNoteId = note.id,
            title = note.title,
            body = note.body,
            selectedTags = note.tags,
            selectedFolder = note.folder,
        )
    }

    fun onEvent(event: CreateEvent) {
        when (event) {
            is CreateEvent.TitleChanged -> updateText(
                newTitle = event.value,
                newBody = _uiState.value.body,
            )

            is CreateEvent.BodyChanged -> updateText(
                newTitle = _uiState.value.title,
                newBody = event.value,
            )

            is CreateEvent.UndoEdit -> {
                if (undoStack.isEmpty()) return
                val current = TextSnapshot(_uiState.value.title, _uiState.value.body)
                redoStack.addLast(current)
                val prev = undoStack.removeLast()
                _uiState.update { it.copy(
                    title = prev.title,
                    body = prev.body,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = true,
                ) }
            }

            is CreateEvent.RedoEdit -> {
                if (redoStack.isEmpty()) return
                val current = TextSnapshot(_uiState.value.title, _uiState.value.body)
                undoStack.addLast(current)
                val next = redoStack.removeLast()
                _uiState.update { it.copy(
                    title = next.title,
                    body = next.body,
                    canUndo = true,
                    canRedo = redoStack.isNotEmpty(),
                ) }
            }

            is CreateEvent.TagToggled -> {
                _uiState.update { state ->
                    val selected = state.selectedTags.toMutableList()
                    if (selected.any { it.id == event.tag.id }) selected.removeAll { it.id == event.tag.id }
                    else selected.add(event.tag)
                    state.copy(selectedTags = selected)
                }
            }

            is CreateEvent.NewTagCreated -> {
                val newTag = Tag(name = event.name)
                _uiState.update { state ->
                    state.copy(
                        availableTags = state.availableTags + newTag,
                        selectedTags = state.selectedTags + newTag,
                    )
                }
            }

            is CreateEvent.FolderSelected ->
                _uiState.update { it.copy(selectedFolder = event.folder, showFolderPicker = false) }

            is CreateEvent.ShowTagPicker ->
                _uiState.update { it.copy(showTagPicker = true) }

            is CreateEvent.DismissTagPicker ->
                _uiState.update { it.copy(showTagPicker = false) }

            is CreateEvent.ShowFolderPicker ->
                _uiState.update { it.copy(showFolderPicker = true) }

            is CreateEvent.DismissFolderPicker ->
                _uiState.update { it.copy(showFolderPicker = false) }

            is CreateEvent.SaveNote -> {
                val state = _uiState.value
                NoteRepository.addOrUpdate(
                    Note(
                        id = state.editingNoteId ?: java.util.UUID.randomUUID().toString(),
                        title = state.title.ifBlank { "Untitled" },
                        body = state.body,
                        tags = state.selectedTags,
                        folder = state.selectedFolder,
                    )
                )
                _uiState.value = CreateUiState()
                undoStack.clear()
                redoStack.clear()
                _navigateBack.tryEmit(Unit)
            }
        }
    }

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    /** 文本变更时推入历史栈，清空重做栈 */
    private fun updateText(newTitle: String, newBody: String) {
        val current = TextSnapshot(_uiState.value.title, _uiState.value.body)
        // 内容无变化时不入栈（例如光标移动触发的无意义更新）
        if (current.title == newTitle && current.body == newBody) return

        undoStack.addLast(current)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()

        _uiState.update { it.copy(
            title = newTitle,
            body = newBody,
            canUndo = true,
            canRedo = false,
        ) }
    }
}
