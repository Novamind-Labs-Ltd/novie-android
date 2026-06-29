package com.novamind.app.feature.library

import com.novamind.app.feature.note.NoteItem

/** Library 文件夹分组（UI 模型）：按笔记所属文件夹聚合。 */
data class LibraryFolder(
    val name: String,
    val noteCount: Int,
    /** 文件夹颜色 #RRGGBB；null = 默认（品牌绿）。 */
    val colorHex: String? = null,
)

/** Recent 页的视图模式：双列网格 / 单列列表。 */
enum class LibraryViewMode { GRID, LIST }

/**
 * Library 页面状态。
 * [notes] 为空时 Recent 页展示空状态，非空时展示笔记网格——由数据自动切换。
 * [folders] 为按文件夹聚合的分组，驱动 Folders 页。
 */
data class LibraryUiState(
    val notes: List<NoteItem> = emptyList(),
    val folders: List<LibraryFolder> = emptyList(),
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
)
