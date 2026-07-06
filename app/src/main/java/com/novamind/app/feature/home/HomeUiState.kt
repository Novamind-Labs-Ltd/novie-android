package com.novamind.app.feature.home

import com.novamind.app.feature.create.model.NoteItem

data class UpcomingItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconResId: Int,
)

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val upcomingItems: List<UpcomingItem> = emptyList(),
    val notes: List<NoteItem> = emptyList(),
    val errorMessage: String? = null,
)
