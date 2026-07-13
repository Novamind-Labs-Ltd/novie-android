package com.novamind.app.feature.recyclebin

import com.novamind.app.feature.create.model.NoteItem

/**
 * 回收站页面状态。[notes] 为空时展示空状态，非空时展示已软删笔记的网格。
 * 列表项复用 [NoteItem]，其 updatedAt 即软删时间，用于计算「剩余 N 天」。
 */
data class RecycleBinUiState(
    val notes: List<NoteItem> = emptyList(),
    /** 正在清空回收站（逐条 DELETE + 刷新期间为 true），驱动全局 loading 遮罩。 */
    val isEmptying: Boolean = false,
)
