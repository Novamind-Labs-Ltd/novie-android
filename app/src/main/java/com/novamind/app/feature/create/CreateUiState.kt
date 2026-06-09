package com.novamind.app.feature.create

import com.novamind.app.feature.create.model.Folder
import com.novamind.app.feature.create.model.Tag

sealed class CreateEvent {
    data class TitleChanged(val value: String) : CreateEvent()
    data class BodyChanged(val value: String) : CreateEvent()
    data class TagToggled(val tag: Tag) : CreateEvent()
    data class NewTagCreated(val name: String) : CreateEvent()
    data class FolderSelected(val folder: Folder?) : CreateEvent()
    object SaveNote : CreateEvent()
    object DismissTagPicker : CreateEvent()
    object ShowTagPicker : CreateEvent()
    object DismissFolderPicker : CreateEvent()
    object ShowFolderPicker : CreateEvent()
}

data class CreateUiState(
    val title: String = "",
    val body: String = "",
    val selectedTags: List<Tag> = emptyList(),
    val selectedFolder: Folder? = null,

    // 可选项数据
    val availableTags: List<Tag> = defaultTags,
    val availableFolders: List<Folder> = defaultFolders,

    // 弹窗状态
    val showTagPicker: Boolean = false,
    val showFolderPicker: Boolean = false,

)

val defaultTags = listOf(
    Tag(id = "t1", name = "Research", colorHex = "#3D7A5A"),
    Tag(id = "t2", name = "Strategy", colorHex = "#7A6D3D"),
    Tag(id = "t3", name = "Design", colorHex = "#3D5A7A"),
    Tag(id = "t4", name = "Meeting", colorHex = "#7A3D5A"),
    Tag(id = "t5", name = "Personal", colorHex = "#5A3D7A"),
    Tag(id = "t6", name = "Product", colorHex = "#3D7A6D"),
)

val defaultFolders = listOf(
    Folder(id = "f1", name = "Work", iconEmoji = "💼"),
    Folder(id = "f2", name = "Personal", iconEmoji = "🏠"),
    Folder(id = "f3", name = "Projects", iconEmoji = "🚀"),
    Folder(id = "f4", name = "Archive", iconEmoji = "📦"),
    Folder(id = "f5", name = "Ideas", iconEmoji = "💡"),
)
