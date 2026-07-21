package com.novamind.app.feature.library

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.R
import com.novamind.app.feature.create.model.NoteItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.novamind.app.feature.library.components.BgPage
import com.novamind.app.feature.library.components.ChangeFolderColorSheet
import com.novamind.app.feature.library.components.ColorAccent
import com.novamind.app.feature.library.components.ColorIconBtn
import com.novamind.app.feature.library.components.ColorTextSub
import com.novamind.app.feature.library.components.ColorTextTitle
import com.novamind.app.feature.library.components.CannotDeleteFolderDialog
import com.novamind.app.feature.library.components.DeleteFolderDialog
import com.novamind.app.feature.library.components.EmptyState
import com.novamind.app.feature.library.components.FolderNoteRow
import com.novamind.app.feature.library.components.FolderRenameRow
import com.novamind.app.feature.library.components.FolderRow
import com.novamind.app.feature.library.components.FolderSwipeRow
import com.novamind.app.feature.library.components.LibraryNoteRow
import com.novamind.app.feature.library.components.SegmentedTabBar
import com.novamind.app.feature.library.components.TopIconButton
import com.novamind.app.feature.library.components.ViewModeToggle
import com.novamind.app.feature.library.components.sampleFolders
import com.novamind.app.feature.library.components.sampleNotes
import com.novamind.app.ui.components.AppPullToRefresh
import com.novamind.app.ui.components.BackButton
import com.novamind.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onCreateNote: () -> Unit = {},
    onToggleViewMode: () -> Unit = {},
    onRefresh: () -> Unit = {},   // Recent 页下拉刷新 → 重拉笔记与文件夹
    onLoadMore: () -> Unit = {},   // Recent 页上拉触底 → 加载下一页
    onLoadMoreFolders: () -> Unit = {},   // Folders 页上拉触底 → 加载下一页
    onOpenSidebar: () -> Unit = {},   // 点击左上角侧栏按钮 → 由宿主（Route）打开抽屉
    onOpenNote: (String) -> Unit = {},   // 点击 Recent 笔记 → 进入笔记预览/编辑页
    onOpenFolder: (String) -> Unit = {},   // 点击文件夹 → 进入该文件夹的笔记列表页
    onCreateFolder: (name: String, colorHex: String?) -> Unit = { _, _ -> },   // Folders 页创建新文件夹
    onReorderFolders: (List<String>) -> Unit = {},   // Folders 页拖拽排序后回传新顺序
    onRenameFolder: (old: String, new: String) -> Unit = { _, _ -> },   // 文件夹「更多 → Rename」
    onDeleteFolder: (String) -> Unit = {},   // 文件夹「更多 → Delete」
    onChangeFolderColor: (name: String, colorHex: String?) -> Unit = { _, _ -> },   // 文件夹「更多 → Change color」
    // 分段标签页状态：由宿主托管，进入文件夹详情再返回时保持在 Folders 页
    pagerState: PagerState = rememberPagerState(pageCount = { 2 }),
    onBack: (() -> Unit)? = null,   // 非 null：左上角显示返回键并触发；null：保持现状（侧栏入口）
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // 「创建文件夹」弹层显隐（仅 Folders 页点击创建时弹出）
    var showCreateFolder by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding()
            .padding(bottom = 100.dp),
    ) {
        // 顶部应用栏（Figma top_info）：左侧「侧栏/返回 + Library 标题」内联一行，
        // 右侧为上下文操作（Recent 页 → 视图切换；Folders 页 → 新建文件夹）。
        // 新建笔记走底部导航中央 FAB，故此处不再放搜索/新建笔记按钮。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    // 作为子页进入（如首页 See all）：返回键，通用组件（与 Create 等页统一）
                    BackButton(onClick = onBack, background = ColorIconBtn, tint = ColorTextTitle)
                } else {
                    // 侧栏入口 → 通知宿主打开抽屉
                    TopIconButton(
                        iconRes = R.drawable.ic_panel_left,
                        desc = "Sidebar",
                        shape = RoundedCornerShape(12.dp),
                        onClick = onOpenSidebar,
                    )
                }
                Text(
                    text = "Library",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorTextTitle,
                )
            }
            if (pagerState.currentPage == 0) {
                ViewModeToggle(viewMode = uiState.viewMode, onClick = onToggleViewMode)
            } else {
                TopIconButton(
                    iconRes = R.drawable.ic_folder_add,
                    desc = "New folder",
                    shape = RoundedCornerShape(12.dp),
                    onClick = { showCreateFolder = true },
                )
            }
        }

        SegmentedTabBar(
            selectedIndex = pagerState.currentPage,
            // 指示条位置随手指滑动平滑过渡（含翻页中间态）
            indicatorFraction = pagerState.currentPage + pagerState.currentPageOffsetFraction,
            onTabClick = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                0 -> RecentPage(
                    uiState = uiState,
                    onCreateNote = onCreateNote,
                    onOpenNote = onOpenNote,
                    onRefresh = onRefresh,
                    onLoadMore = onLoadMore,
                )
                else -> FoldersPage(
                    folders = uiState.folders,
                    onOpenFolder = onOpenFolder,
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    hasMoreFolders = uiState.hasMoreFolders,
                    isLoadingMoreFolders = uiState.isLoadingMoreFolders,
                    onLoadMoreFolders = onLoadMoreFolders,
                    onReorder = onReorderFolders,
                    onRenameFolder = onRenameFolder,
                    onDeleteFolder = onDeleteFolder,
                    onChangeFolderColor = onChangeFolderColor,
                )
            }
        }
    }

    // 创建文件夹弹层（Folders 页点击创建时）
    if (showCreateFolder) {
        val context = LocalContext.current
        CreateFolderSheet(
            onCreate = { name, colorHex ->
                // 重名校验（忽略大小写）：已存在则提示且不创建、不关闭弹窗
                if (uiState.folders.any { it.name.equals(name, ignoreCase = true) }) {
                    Toast.makeText(context, "Folder \"$name\" already exists", Toast.LENGTH_SHORT).show()
                } else {
                    showCreateFolder = false
                    onCreateFolder(name, colorHex)
                }
            },
            onDismiss = { showCreateFolder = false },
        )
    }
}

/**
 * Recent 页：无笔记显示空状态；有笔记按 viewMode 显示双列网格或单列列表。点击笔记进入预览/编辑页。
 * 整页包裹下拉刷新（与首页一致）：下拉重拉笔记与文件夹；空态也置于可滚动容器内以支持下拉。
 */
@Composable
private fun RecentPage(
    uiState: LibraryUiState,
    onCreateNote: () -> Unit,
    onOpenNote: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit = {},
) {
    val gridState = rememberLazyStaggeredGridState()
    val listState = rememberLazyListState()
    val isGrid = uiState.viewMode == LibraryViewMode.GRID

    // 触底检测：最后可见项接近末尾且还有下一页时触发加载（grid / list 各自的 layoutInfo）
    val reachedEnd by remember {
        derivedStateOf {
            val (last, total) = if (isGrid) {
                (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to
                    gridState.layoutInfo.totalItemsCount
            } else {
                (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to
                    listState.layoutInfo.totalItemsCount
            }
            total > 0 && last >= total - 4
        }
    }
    LaunchedEffect(reachedEnd, uiState.hasMoreNotes, uiState.isLoadingMore) {
        if (reachedEnd && uiState.hasMoreNotes && !uiState.isLoadingMore) onLoadMore()
    }

    AppPullToRefresh(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            uiState.notes.isEmpty() ->
                // 空态放进 LazyColumn（单项撑满视口），既能居中显示、又能下拉刷新
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        EmptyState(onCreateNote = onCreateNote, modifier = Modifier.fillParentMaxSize())
                    }
                }

            isGrid ->
                // 双列瀑布流（Figma）：卡片按内容高度自适应、交错排列
                LazyVerticalStaggeredGrid(
                    state = gridState,
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalItemSpacing = 14.dp,
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                ) {
                    items(uiState.notes, key = { it.id }) { note ->
                        LibraryNoteCard(note = note, onClick = { onOpenNote(note.id) })
                    }
                    if (uiState.isLoadingMore) {
                        item(span = StaggeredGridItemSpan.FullLine) { LoadMoreFooter() }
                    }
                }

            else ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                ) {
                    items(uiState.notes, key = { it.id }) { note ->
                        LibraryNoteRow(note = note, onClick = { onOpenNote(note.id) })
                    }
                    if (uiState.isLoadingMore) {
                        item { LoadMoreFooter() }
                    }
                }
        }
    }
}

/** 列表底部「加载中」指示器（上拉加载下一页时显示）。 */
@Composable
private fun LoadMoreFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = ColorTextSub,
        )
    }
}

/**
 * Folders 页：按文件夹聚合的列表，空则显示提示。
 * 支持长按某行拖拽排序；松手后通过 [onReorder] 回传新的名称顺序。
 * 整页包裹下拉刷新（与首页 / Recent 页一致）：下拉重拉笔记与文件夹；空态也置于可滚动容器内以支持下拉。
 */
@Composable
private fun FoldersPage(
    folders: List<LibraryFolder>,
    onOpenFolder: (String) -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    hasMoreFolders: Boolean = false,
    isLoadingMoreFolders: Boolean = false,
    onLoadMoreFolders: () -> Unit = {},
    onReorder: (List<String>) -> Unit = {},
    onRenameFolder: (old: String, new: String) -> Unit = { _, _ -> },
    onDeleteFolder: (String) -> Unit = {},
    onChangeFolderColor: (name: String, colorHex: String?) -> Unit = { _, _ -> },
) {
    // 重命名 / 删除 / 改色目标文件夹名（null = 不显示对应弹窗）
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var colorTarget by remember { mutableStateOf<String?>(null) }
    // 当前左滑展开的文件夹名（同时最多一行展开；打开新行自动收起其它行）
    var openSwipeName by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // 拖拽换序：sh.calvin.reorderable（长按整行拖动）。ordered 为本地顺序副本：拖动中由 onMove 改写、
    // 非拖拽时从服务端 folders 同步；抬起（onDragStopped）时把顺序（文件夹名序列）提交给 onReorder。
    val haptics = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    var ordered by remember { mutableStateOf(folders) }
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        ordered = ordered.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    LaunchedEffect(folders, reorderState.isAnyItemDragging) {
        if (!reorderState.isAnyItemDragging) ordered = folders
    }

    // 触底检测：最后可见项接近末尾且还有下一页时加载更多（拖拽中不触发）
    val reachedEnd by remember {
        derivedStateOf {
            val last = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = lazyListState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 3
        }
    }
    LaunchedEffect(reachedEnd, hasMoreFolders, isLoadingMoreFolders) {
        if (reachedEnd && hasMoreFolders && !isLoadingMoreFolders && !reorderState.isAnyItemDragging) {
            onLoadMoreFolders()
        }
    }

    AppPullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (folders.isEmpty()) {
            // 空态放进 LazyColumn（单项撑满视口），既能居中显示、又能下拉刷新
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No folders yet.",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = ColorTextSub,
                        )
                    }
                }
            }
            return@AppPullToRefresh
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
        ) {
            items(ordered, key = { it.name }) { folder ->
            ReorderableItem(reorderState, key = folder.name) { _ ->
                if (folder.name == renameTarget) {
                    // 行内重命名：× 取消 + 输入框 + 绿色 ✓ 确认（重命名态不参与拖拽）
                    FolderRenameRow(
                        initialName = folder.name,
                        colorHex = folder.colorHex,
                        onConfirm = { newName ->
                            // 与其它已有文件夹重名（忽略大小写）→ 提示且不修改
                            val conflict = folders.any {
                                it.name != folder.name && it.name.equals(newName, ignoreCase = true)
                            }
                            if (conflict) {
                                Toast.makeText(
                                    context,
                                    "Folder \"$newName\" already exists",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                onRenameFolder(folder.name, newName)
                                renameTarget = null
                            }
                        },
                        onCancel = { renameTarget = null },
                    )
                } else {
                    // 长按整行拖拽排序（外层把手）；横向左滑露出 编辑/删除（FolderSwipeRow）
                    Box(
                        modifier = Modifier.longPressDraggableHandle(
                            onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDragStopped = { onReorder(ordered.map { it.name }) },
                        ),
                    ) {
                        FolderSwipeRow(
                            open = openSwipeName == folder.name,
                            onOpenChange = { opened -> openSwipeName = if (opened) folder.name else null },
                            onEdit = { renameTarget = folder.name },
                            onDelete = { deleteTarget = folder.name },
                        ) { isOpen, close ->
                            FolderRow(
                                folder = folder,
                                // 展开态点击整行先收起，避免误入文件夹
                                onClick = { if (isOpen) close() else onOpenFolder(folder.name) },
                                onRename = { renameTarget = folder.name },
                                onChangeColor = { colorTarget = folder.name },
                                onDelete = { deleteTarget = folder.name },
                            )
                        }
                    }
                }
            }
            }
            if (isLoadingMoreFolders) {
                item { LoadMoreFooter() }
            }
        }
    }

    // 删除：文件夹内仍有笔记 → 提示不可删除；否则二次确认后删除
    deleteTarget?.let { target ->
        val hasNotes = (folders.firstOrNull { it.name == target }?.noteCount ?: 0) > 0
        if (hasNotes) {
            CannotDeleteFolderDialog(onDismiss = { deleteTarget = null })
        } else {
            DeleteFolderDialog(
                folderName = target,
                onConfirm = {
                    onDeleteFolder(target)
                    deleteTarget = null
                },
                onDismiss = { deleteTarget = null },
            )
        }
    }

    // 改颜色底部弹层
    colorTarget?.let { target ->
        val currentHex = folders.firstOrNull { it.name == target }?.colorHex
        ChangeFolderColorSheet(
            currentHex = currentHex,
            onPick = { hex ->
                onChangeFolderColor(target, hex)
                colorTarget = null
            },
            onDismiss = { colorTarget = null },
        )
    }
}

/**
 * 文件夹详情页：点击某文件夹后进入，展示该文件夹下的笔记列表。
 * 顶部返回 / 搜索 / 更多，标题区为文件夹图标 + 名称 + 排序按钮。
 */
@Composable
internal fun FolderDetailScreen(
    folderName: String,
    notes: List<NoteItem>,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var descending by rememberSaveable(folderName) { mutableStateOf(true) }
    val sorted = remember(notes, descending) { if (descending) notes else notes.asReversed() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding()
            .padding(bottom = 100.dp),
    ) {
        // 顶部工具条：返回 / 搜索 / 更多
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack, background = ColorIconBtn, tint = ColorTextTitle)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopIconButton(R.drawable.ic_search, "Search", shape = CircleShape)
                TopIconButton(R.drawable.ic_more, "More", shape = CircleShape, bg = Color.Transparent)
            }
        }

        // 标题区：文件夹图标 + 名称 + 排序按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ColorAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    tint = ColorAccent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = folderName,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ColorTextTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            // 排序按钮：切换正序 / 倒序
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { descending = !descending },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sort),
                    contentDescription = if (descending) "Sort ascending" else "Sort descending",
                    tint = ColorTextTitle,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        if (sorted.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No notes in this folder.", fontSize = 15.sp, color = ColorTextSub)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
            ) {
                items(sorted, key = { it.id }) { note ->
                    FolderNoteRow(note = note, onClick = { onOpenNote(note.id) })
                }
            }
        }
    }
}

@Composable
fun LibraryRoute(
    onCreateNote: () -> Unit = {},
    onOpenNote: (String) -> Unit = {},
    onOpenTagManager: () -> Unit = {},
    onOpenSharedWithMe: () -> Unit = {},
    onOpenRecycleBin: () -> Unit = {},
    onFullscreenChange: (Boolean) -> Unit = {},   // 抽屉打开 → 宿主隐藏底部导航，让抽屉盖住底栏
    onBack: (() -> Unit)? = null,   // 非 null：作为子页进入，左上角为返回键
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // 每次进入 Library（重新进入组合，如底栏切换 / 从编辑器返回）都静默重拉笔记与文件夹，与首页一致
    LaunchedEffect(Unit) { viewModel.reload() }
    // 当前进入的文件夹（详情页）；null = 显示 Library 主页（Recent/Folders）
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    // 分段标签状态提升到这里：进入文件夹详情再返回时仍停留在 Folders 页（不回到 Recent）
    val pagerState = rememberPagerState(pageCount = { 2 })

    BackHandler(enabled = selectedFolder != null) { selectedFolder = null }

    AnimatedContent(
        targetState = selectedFolder,
        transitionSpec = {
            if (targetState != null) {
                // 进入文件夹详情：从右侧推入
                (slideInHorizontally { it } + fadeIn())
                    .togetherWith(slideOutHorizontally { -it / 4 } + fadeOut())
            } else {
                // 返回：向右滑出详情，主页从左侧回入
                (slideInHorizontally { -it / 4 } + fadeIn())
                    .togetherWith(slideOutHorizontally { it } + fadeOut())
            }
        },
        label = "library_folder_detail",
    ) { folder ->
        if (folder != null) {
            // 该文件夹下的笔记：Unfiled 对应无文件夹的笔记
            val folderNotes = uiState.notes.filter {
                if (folder == "Unfiled") it.folderName == null else it.folderName == folder
            }
            FolderDetailScreen(
                folderName = folder,
                notes = folderNotes,
                onBack = { selectedFolder = null },
                onOpenNote = onOpenNote,
                modifier = modifier,
            )
        } else {
            // 抽屉宿主（push-reveal）：点击左上角侧栏按钮打开——主内容右移露出底层的文件夹菜单。
            var drawerOpen by remember { mutableStateOf(false) }
            // 抽屉打开时通知宿主隐藏底部导航栏（盖住底栏）；离开时复位
            LaunchedEffect(drawerOpen) { onFullscreenChange(drawerOpen) }
            DisposableEffect(Unit) { onDispose { onFullscreenChange(false) } }
            BackHandler(enabled = drawerOpen) { drawerOpen = false }

            PushRevealDrawer(
                open = drawerOpen,
                onOpenChange = { drawerOpen = it },
                drawer = {
                    LibraryDrawer(
                        folders = uiState.folders,
                        onOpenFolder = { name -> drawerOpen = false; selectedFolder = name },
                        onOpenTagManager = { drawerOpen = false; onOpenTagManager() },
                        onOpenSharedWithMe = { drawerOpen = false; onOpenSharedWithMe() },
                        onOpenRecycleBin = { drawerOpen = false; onOpenRecycleBin() },
                    )
                },
            ) {
                LibraryScreen(
                    uiState = uiState,
                    onCreateNote = onCreateNote,
                    onToggleViewMode = viewModel::toggleViewMode,
                    onRefresh = viewModel::onRefresh,
                    onLoadMore = viewModel::loadMoreNotes,
                    onLoadMoreFolders = viewModel::loadMoreFolders,
                    onOpenSidebar = { drawerOpen = true },
                    onOpenNote = onOpenNote,
                    onOpenFolder = { selectedFolder = it },
                    onCreateFolder = viewModel::createFolder,
                    onReorderFolders = viewModel::reorderFolders,
                    onRenameFolder = viewModel::renameFolder,
                    onDeleteFolder = viewModel::deleteFolder,
                    onChangeFolderColor = viewModel::changeFolderColor,
                    pagerState = pagerState,
                    onBack = onBack,
                    modifier = modifier,
                )
            }
        }
    }
}

/** push-reveal 抽屉主内容右移比例（露出底层菜单的宽度占屏宽比）。 */
private const val REVEAL_FRACTION = 0.70f

/**
 * Push-reveal 抽屉：底层为 [drawer]（文件夹菜单），[content] 为主内容。
 * 打开时主内容整体右移 + 轻微缩小 + 圆角 + 投影 + 压暗，露出底层菜单。
 *
 * 手势跟手（nested scroll）：主内容**向右拖**——当内部 Pager 已在最左页无法再右滑时，
 * 剩余的右向位移用来逐步拉开抽屉（进度跟手指走）；向左拖则收回。松手过 40% 吸附到开/关。
 * 打开态还可直接点击露出的主内容关闭。[onOpenChange] 回报最终开合状态（供宿主隐藏底栏等）。
 */
@Composable
private fun PushRevealDrawer(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    drawer: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 进度 0（关）..1（开）。外部 open 变化时动画过渡；拖动时直接 snapTo 跟手。
    val progress = remember { Animatable(if (open) 1f else 0f) }
    var revealPx by remember { mutableFloatStateOf(1f) }
    // 是否需要把底层抽屉纳入布局/命中测试：仅在打开或拖动中为 true。
    // 完全关闭时不放抽屉，避免主内容空白处（如标题右侧）的点击透传到底层抽屉。
    var revealed by remember { mutableStateOf(open) }
    LaunchedEffect(open) {
        if (open) revealed = true
        progress.animateTo(if (open) 1f else 0f, tween(300))
        if (!open) revealed = false
    }

    fun drag(deltaPx: Float) {
        revealed = true
        scope.launch { progress.snapTo((progress.value + deltaPx / revealPx).coerceIn(0f, 1f)) }
    }
    fun settle() {
        val target = if (progress.value > 0.4f) 1f else 0f
        scope.launch {
            progress.animateTo(target, tween(220))
            if (target == 0f) revealed = false
        }
        onOpenChange(target == 1f)
    }

    // 嵌套滚动：把 Pager/列表消费不掉的水平位移转成抽屉进度
    val nested = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // 抽屉已被拉开一部分：优先由抽屉消费水平位移（冻结内部 Pager，双向跟手）
            return if (progress.value > 0f && available.x != 0f) {
                drag(available.x); Offset(available.x, 0f)
            } else {
                Offset.Zero
            }
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            // Pager 已到最左仍向右拖（available.x>0）：用剩余量拉开抽屉
            return if (available.x > 0f) {
                drag(available.x); Offset(available.x, 0f)
            } else {
                Offset.Zero
            }
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            // 拖动中途松手：吸附到开/关，并吃掉这段 fling 速度避免 Pager 续滑
            return if (progress.value > 0f && progress.value < 1f) {
                settle(); available
            } else {
                Velocity.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { revealPx = (it.width * REVEAL_FRACTION).coerceAtLeast(1f) },
    ) {
        // 底层：文件夹侧边菜单（仅在打开/拖动中放入布局，避免关闭态点击透传到抽屉）
        if (revealed) {
            drawer()
        }
        // 上层：主内容，随进度右移 / 缩小 / 圆角 / 投影（在 graphicsLayer 里读 progress，避免整页重组）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nested)
                .graphicsLayer {
                    val p = progress.value
                    translationX = size.width * REVEAL_FRACTION * p
                    val s = 1f - 0.10f * p
                    scaleX = s
                    scaleY = s
                    shadowElevation = 24f * p
                    shape = RoundedCornerShape(32.dp * p)
                    clip = p > 0f
                },
        ) {
            content()
            // 压暗遮罩：alpha 随进度（draw 阶段读取，不触发重组）；仅打开态可拖/可点关闭
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.25f * progress.value }
                    .background(Color.Black)
                    .then(
                        if (open) {
                            Modifier
                                .pointerInput(Unit) { detectTapGestures { onOpenChange(false) } }
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { _, d -> drag(d) },
                                        onDragEnd = { settle() },
                                    )
                                }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LibraryEmptyPreview() {
    AppTheme { LibraryScreen(uiState = LibraryUiState(notes = emptyList())) }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LibraryPopulatedPreview() {
    AppTheme { LibraryScreen(uiState = LibraryUiState(notes = sampleNotes, folders = sampleFolders)) }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LibraryListModePreview() {
    AppTheme {
        LibraryScreen(
            uiState = LibraryUiState(
                notes = sampleNotes,
                folders = sampleFolders,
                viewMode = LibraryViewMode.LIST,
            )
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Folders Page")
@Composable
private fun LibraryFoldersPreview() {
    AppTheme {
        LibraryScreen(
            uiState = LibraryUiState(notes = sampleNotes, folders = sampleFolders),
            // 初始停在 Folders 标签页
            pagerState = rememberPagerState(initialPage = 1, pageCount = { 2 }),
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun FolderDetailPreview() {
    AppTheme {
        FolderDetailScreen(
            folderName = "Q3 KPIs",
            notes = sampleNotes,
            onBack = {},
        )
    }
}

@Preview(showBackground = true, name = "Library · FoldersPage")
@Composable
private fun FoldersPagePreview() {
    AppTheme {
        FoldersPage(folders = sampleFolders, onOpenFolder = {})
    }
}

@Preview(showBackground = true, name = "Library · RecentPage Grid")
@Composable
private fun RecentPageGridPreview() {
    AppTheme {
        RecentPage(
            uiState = LibraryUiState(notes = sampleNotes, viewMode = LibraryViewMode.GRID),
            onCreateNote = {},
            onOpenNote = {},
            onRefresh = {},
        )
    }
}

@Preview(showBackground = true, name = "Library · RecentPage List")
@Composable
private fun RecentPageListPreview() {
    AppTheme {
        RecentPage(
            uiState = LibraryUiState(notes = sampleNotes, viewMode = LibraryViewMode.LIST),
            onCreateNote = {},
            onOpenNote = {},
            onRefresh = {},
        )
    }
}
