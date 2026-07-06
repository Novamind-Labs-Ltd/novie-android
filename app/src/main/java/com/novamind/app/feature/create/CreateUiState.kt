package com.novamind.app.feature.create

import androidx.compose.ui.graphics.Color
import com.novamind.app.feature.create.folder.Folder
import com.novamind.app.feature.create.tag.Tag

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
    val availableTags: List<Tag> = emptyList(),
    val availableFolders: List<Folder> = emptyList(),

    // 弹窗状态
    val showTagPicker: Boolean = false,
    val showFolderPicker: Boolean = false,
    val showColorPicker: Boolean = false,

    // 撤销/重做可用状态
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,

    // ── 云端同步（基础能力）──
    val remoteId: String? = null,       // 云端笔记 id（创建/拉取后有值）
    val remoteRev: Long? = null,        // 云端乐观锁版本，PUT 更新时携带
    val remoteSyncing: Boolean = false, // 是否有云端请求进行中
    val remoteStatus: String? = null,   // 最近一次云端操作结果提示（一次性，UI 消费后可清）
)
