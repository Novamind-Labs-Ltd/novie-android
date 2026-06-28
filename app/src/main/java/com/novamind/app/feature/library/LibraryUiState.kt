package com.novamind.app.feature.library

/** Library 列表项（UI 模型）。 */
data class LibraryNote(
    val id: String,
    val title: String,
    val preview: String,
    val tag: String,
    /** 所属文件夹名；null = 未归档（Unfiled）。用于文件夹详情页筛选。 */
    val folderName: String? = null,
    /** 展示用的日期标签（如「今天 10:00」「06-01」）。 */
    val dateLabel: String = "",
)

/** Library 文件夹分组（UI 模型）：按笔记所属文件夹聚合。 */
data class LibraryFolder(
    val name: String,
    val noteCount: Int,
)

/** Recent 页的视图模式：双列网格 / 单列列表。 */
enum class LibraryViewMode { GRID, LIST }

/**
 * Library 页面状态。
 * [notes] 为空时 Recent 页展示空状态，非空时展示笔记网格——由数据自动切换。
 * [folders] 为按文件夹聚合的分组，驱动 Folders 页。
 */
data class LibraryUiState(
    val notes: List<LibraryNote> = emptyList(),
    val folders: List<LibraryFolder> = emptyList(),
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
)
