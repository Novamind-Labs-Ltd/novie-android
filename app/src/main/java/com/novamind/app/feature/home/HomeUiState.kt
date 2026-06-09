package com.novamind.app.feature.home

data class UpcomingItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconResId: Int,
)

data class NoteItem(
    val id: String,
    val title: String,
    val description: String,
    val isSelected: Boolean = false,
)

data class HomeUiState(
    val isLoading: Boolean = false,
    val upcomingItems: List<UpcomingItem> = emptyList(),
    val notes: List<NoteItem> = emptyList(),
    val errorMessage: String? = null,
)
