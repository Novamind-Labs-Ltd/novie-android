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
    val tags: List<String> = emptyList(),
    val folderName: String? = null,
    /** 笔记创建时间（epoch 毫秒） */
    val createdAt: Long = 0L,
    /** 笔记最后更新时间（epoch 毫秒） */
    val updatedAt: Long = 0L,
)

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val upcomingItems: List<UpcomingItem> = emptyList(),
    val notes: List<NoteItem> = emptyList(),
    val errorMessage: String? = null,
)
