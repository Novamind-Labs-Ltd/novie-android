package com.novamind.app.feature.home

import com.novamind.app.feature.create.model.NoteItem

data class UpcomingItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconResId: Int,
    /** 计划时间（如「10:00」）；home_final 卡片左侧展示，为空则不显示。 */
    val time: String = "",
)

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val upcomingItems: List<UpcomingItem> = emptyList(),
    val notes: List<NoteItem> = emptyList(),
    val errorMessage: String? = null,
)
