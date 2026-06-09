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

class CreateViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CreateUiState())
    val uiState = _uiState.asStateFlow()

    /** 一次性导航事件，不持久化在 UiState 中 */
    private val _navigateBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateBack = _navigateBack.asSharedFlow()

    fun reset() {
        _uiState.value = CreateUiState()
    }

    fun onEvent(event: CreateEvent) {
        when (event) {
            is CreateEvent.TitleChanged ->
                _uiState.update { it.copy(title = event.value) }

            is CreateEvent.BodyChanged ->
                _uiState.update { it.copy(body = event.value) }

            is CreateEvent.TagToggled -> {
                _uiState.update { state ->
                    val selected = state.selectedTags.toMutableList()
                    if (selected.any { it.id == event.tag.id }) {
                        selected.removeAll { it.id == event.tag.id }
                    } else {
                        selected.add(event.tag)
                    }
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
                        title = state.title.ifBlank { "Untitled" },
                        body = state.body,
                        tags = state.selectedTags,
                        folder = state.selectedFolder,
                    )
                )
                // 重置表单，再发送导航事件
                _uiState.value = CreateUiState()
                _navigateBack.tryEmit(Unit)
            }
        }
    }
}
