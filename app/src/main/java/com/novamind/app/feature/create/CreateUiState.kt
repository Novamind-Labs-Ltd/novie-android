package com.novamind.app.feature.create

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

    // 保存中（正在 POST/PUT 落盘服务端）：用于顶部「Saving…」提示
    val isSaving: Boolean = false,

    // 源录音上传中（§7）：驱动「Uploading for transcription…」进度条
    val isUploadingAudio: Boolean = false,
    val audioUploadProgress: Float = 0f,   // 0..1

    // 转写轮询中（§9）：结果就绪前笔记只读，不可编辑
    val isTranscribing: Boolean = false,
)