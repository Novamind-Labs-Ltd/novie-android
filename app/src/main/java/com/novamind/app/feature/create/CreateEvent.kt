package com.novamind.app.feature.create

import androidx.compose.ui.graphics.Color
import com.novamind.app.feature.create.folder.Folder
import com.novamind.app.feature.create.tag.Tag

/** Create 页的 UI 事件（单向数据流：事件向上）。 */
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
