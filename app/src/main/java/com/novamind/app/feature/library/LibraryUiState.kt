package com.novamind.app.feature.library

/** Library 列表项（UI 模型）。 */
data class LibraryNote(
    val id: String,
    val title: String,
    val preview: String,
    val tag: String,
)

/**
 * Library 页面状态。
 * [notes] 为空时展示空状态页，非空时展示笔记网格——由数据自动切换。
 */
data class LibraryUiState(
    val notes: List<LibraryNote> = emptyList(),
)
