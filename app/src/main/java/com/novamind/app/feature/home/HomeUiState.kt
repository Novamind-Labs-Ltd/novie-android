package com.novamind.app.feature.home

import androidx.compose.ui.graphics.Color

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
    /** 自定义边框颜色；null = 默认边框（hex 仅在数据层转换） */
    val borderColor: Color? = null,
    /** 正文里的第一张图片路径；null = 无图片 */
    val imagePath: String? = null,
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
