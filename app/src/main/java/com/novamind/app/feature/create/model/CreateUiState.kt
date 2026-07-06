package com.novamind.app.feature.create.model

import androidx.compose.ui.graphics.Color
import com.novamind.app.feature.create.folder.Folder
import com.novamind.app.feature.create.tag.Tag

data class CreateUiState(
    val editingNoteId: String? = null,  // null = 新笔记，非 null = 编辑已有笔记
    val updatedAt: Long? = null,        // 编辑已有笔记时的更新时间；新笔记为 null
    val title: String = "",
    val body: String = "",              // 图文混排文档 JSON（旧数据为纯文本）
    val selectedTags: List<Tag> = emptyList(),
    val selectedFolder: Folder? = null,
    val borderColor: Color? = null,     // 自定义边框颜色；null = 默认（hex 仅在落库时转换）

    // 可选项数据
    val availableTags: List<Tag> = emptyList(),
    val availableFolders: List<Folder> = emptyList(),

    // 弹窗状态
    val showTagPicker: Boolean = false,
    val showFolderPicker: Boolean = false,
    val showColorPicker: Boolean = false,

    // 撤销/重做可用状态
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)