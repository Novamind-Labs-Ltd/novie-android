package com.novamind.app.feature.tagmanager

/** Tag 管理页的标签行模型：名称 + 颜色 + 关联笔记数。 */
data class TagRowItem(
    val id: String,
    val name: String,
    val colorHex: String,
    val noteCount: Int,
)

data class TagManagerUiState(
    val tags: List<TagRowItem> = emptyList(),
)
