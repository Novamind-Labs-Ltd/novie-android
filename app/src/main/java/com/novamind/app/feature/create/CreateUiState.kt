package com.novamind.app.feature.create

import androidx.compose.ui.graphics.Color
import com.novamind.app.feature.create.folder.Folder
import com.novamind.app.feature.create.folder.defaultFolders
import com.novamind.app.feature.create.tag.Tag
import com.novamind.app.feature.create.tag.defaultTags

sealed class CreateEvent {
    data class TitleChanged(val value: String) : CreateEvent()
    /** 图文正文变化：传入结构化文档 JSON（存入 body） */
    data class ContentChanged(val document: String) : CreateEvent()
    data class TagToggled(val tag: Tag) : CreateEvent()
    data class NewTagCreated(val name: String) : CreateEvent()
    data class FolderSelected(val folder: Folder?) : CreateEvent()
    data class NewFolderCreated(val name: String) : CreateEvent()
    object SaveNote : CreateEvent()
    object DeleteNote : CreateEvent()
    object UndoEdit : CreateEvent()
    object RedoEdit : CreateEvent()
    object DismissTagPicker : CreateEvent()
    object ShowTagPicker : CreateEvent()
    object DismissFolderPicker : CreateEvent()
    object ShowFolderPicker : CreateEvent()
    object ShowColorPicker : CreateEvent()
    object DismissColorPicker : CreateEvent()
    /** 选中边框颜色；null = 恢复默认边框 */
    data class BorderColorSelected(val color: Color?) : CreateEvent()
}

data class CreateUiState(
    val editingNoteId: String? = null,  // null = 新笔记，非 null = 编辑已有笔记
    val updatedAt: Long? = null,        // 编辑已有笔记时的更新时间；新笔记为 null
    val title: String = "",
    val body: String = "",              // 图文混排文档 JSON（旧数据为纯文本）
    val selectedTags: List<Tag> = emptyList(),
    val selectedFolder: Folder? = null,
    val borderColor: Color? = null,     // 自定义边框颜色；null = 默认（hex 仅在落库时转换）

    // 可选项数据
    val availableTags: List<Tag> = defaultTags,
    val availableFolders: List<Folder> = defaultFolders,

    // 弹窗状态
    val showTagPicker: Boolean = false,
    val showFolderPicker: Boolean = false,
    val showColorPicker: Boolean = false,

    // 撤销/重做可用状态
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)
