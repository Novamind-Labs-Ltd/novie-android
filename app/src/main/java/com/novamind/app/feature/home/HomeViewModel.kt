package com.novamind.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.R
import com.novamind.app.data.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

class HomeViewModel : ViewModel() {

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
        // 订阅仓库，笔记变更时同步更新 Home 状态
        NoteRepository.notes
            .onEach { notes ->
                _uiState.update { state ->
                    state.copy(
                        notes = notes.map { note ->
                            NoteItem(
                                id = note.id,
                                title = note.title,
                                description = note.body,
                                tags = note.tags.map { it.name },
                                folderName = note.folder?.name,
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
}
