package com.novamind.app.feature.create.model

import androidx.compose.ui.graphics.Color

/** 笔记列表项（UI 模型）：首页、Library 等多处共用。 */
data class NoteItem(
    val id: String,
    val title: String,
    /** 纯文本摘要（服务端 preview：正文首行、≤50 字）；列表副标题展示用。 */
    val preview: String,
    val isSelected: Boolean = false,
    val tags: List<String> = emptyList(),
    /** 自定义边框颜色；null = 默认边框（hex 仅在数据层转换） */
    val borderColor: Color? = null,
    /**
     * 缩略图来源：列表接口的缩略图签名 URL（http…）或正文首图本地文件路径；null = 无图。
     * 卡片按 `startsWith("http")` 区分远端 URL 与本地 File 加载。
     */
    val imagePath: String? = null,
    /** 所属文件夹名；null = 未归档（Unfiled）。Library 按此聚合/筛选。 */
    val folderName: String? = null,
    /** 笔记创建时间（epoch 毫秒） */
    val createdAt: Long = 0L,
    /** 笔记最后更新时间（epoch 毫秒） */
    val updatedAt: Long = 0L,
)