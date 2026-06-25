package com.novamind.app.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.NovieApplication
import com.novamind.app.R
import com.novamind.app.feature.create.editor.NoteDocument
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val noteRepository = (application as NovieApplication).noteRepository

    private val _uiState = MutableStateFlow(
        HomeUiState(
            upcomingItems = listOf(
                UpcomingItem(
                    id = "1",
                    title = "Monthly report sharing",
                    subtitle = "Team project progress tracking",
                    iconResId = R.drawable.ic_upcoming_report,
                ),
                UpcomingItem(
                    id = "2",
                    title = "Board meeting",
                    subtitle = "Internal stakeholder alignment",
                    iconResId = R.drawable.ic_upcoming_meeting,
                ),
            ),
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        noteRepository.notes
            .onEach { notes ->
                _uiState.update { state ->
                    state.copy(
                        notes = notes.map { note ->
                            NoteItem(
                                id = note.id,
                                title = note.title,
                                description = NoteDocument.previewText(note.body),
                                tags = note.tags.map { it.name },
                                createdAt = note.createdAt,
                                updatedAt = note.updatedAt,
                            )
                        }
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChange(query: String) {
        // TODO: filter
    }

    /** 下拉刷新：笔记由 Room Flow 实时驱动，这里仅做刷新态展示（后续可接服务端拉取） */
    fun onRefresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            delay(600)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
