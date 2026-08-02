package com.novamind.app.feature.create.tag.tagmanager

/** Tag 管理页的标签行模型：名称 + 颜色。 */
data class TagRowItem(
    val id: String,
    val name: String,
    val colorHex: String,
)

data class TagManagerUiState(
    val tags: List<TagRowItem> = emptyList(),
)
