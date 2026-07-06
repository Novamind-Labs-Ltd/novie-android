package com.novamind.app.feature.create.model

import androidx.compose.ui.graphics.Color

/** 笔记列表项（UI 模型）：首页、Library 等多处共用。 */
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
    /** 所属文件夹名；null = 未归档（Unfiled）。Library 按此聚合/筛选。 */
    val folderName: String? = null,
    /** 笔记创建时间（epoch 毫秒） */
    val createdAt: Long = 0L,
    /** 笔记最后更新时间（epoch 毫秒） */
    val updatedAt: Long = 0L,
)