package com.novamind.app.feature.library

import com.novamind.app.feature.create.model.NoteItem

/** Library 文件夹分组（UI 模型）：按文件夹聚合。 */
data class LibraryFolder(
    val name: String,
    val noteCount: Int,
    /** 文件夹颜色 #RRGGBB；null = 默认（品牌绿）。颜色为本地概念，服务端不带。 */
    val colorHex: String? = null,
    /** 服务端文件夹 id；null = 虚拟分组（如 Unfiled）或尚未在服务端建档。 */
    val id: String? = null,
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
    /** 首屏加载中（首次拉取笔记完成前为 true）：Recent 页据此先出骨架图，避免空态一闪。 */
    val isInitialLoading: Boolean = true,
    /** Recent 页正在下拉刷新（重拉笔记 + 文件夹期间为 true），驱动下拉刷新指示器。 */
    val isRefreshing: Boolean = false,
    /** Recent 页是否还有下一页（分页游标非空）。 */
    val hasMoreNotes: Boolean = false,
    /** Recent 页正在上拉加载下一页，驱动列表底部加载指示器。 */
    val isLoadingMore: Boolean = false,
    /** Folders 页是否还有下一页（分页游标非空）。 */
    val hasMoreFolders: Boolean = false,
    /** Folders 页正在上拉加载下一页，驱动列表底部加载指示器。 */
    val isLoadingMoreFolders: Boolean = false,
    /** 文件夹详情页：当前文件夹内的笔记（按 folderId 独立拉取，与 Recent 解耦）。 */
    val folderNotes: List<NoteItem> = emptyList(),
    /** 文件夹详情页是否还有下一页。 */
    val folderNotesHasMore: Boolean = false,
    /** 文件夹详情页正在上拉加载下一页。 */
    val folderNotesLoading: Boolean = false,
)
