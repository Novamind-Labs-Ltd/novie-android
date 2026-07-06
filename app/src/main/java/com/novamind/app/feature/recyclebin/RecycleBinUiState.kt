package com.novamind.app.feature.recyclebin

import com.novamind.app.feature.create.model.NoteItem

/**
 * 回收站页面状态。[notes] 为空时展示空状态，非空时展示已软删笔记的网格。
 * 列表项复用 [NoteItem]，其 updatedAt 即软删时间，用于计算「剩余 N 天」。
 */
data class RecycleBinUiState(
    val notes: List<NoteItem> = emptyList(),
)
