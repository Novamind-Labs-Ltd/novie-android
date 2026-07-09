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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.novamind.app.feature.library.components.BgPage
import com.novamind.app.feature.library.components.ChangeFolderColorSheet
import com.novamind.app.feature.library.components.ColorAccent
import com.novamind.app.feature.library.components.ColorIconBtn
import com.novamind.app.feature.library.components.ColorTextSub
import com.novamind.app.feature.library.components.ColorTextTitle
import com.novamind.app.feature.library.components.DeleteFolderDialog
import com.novamind.app.feature.library.components.EmptyState
import com.novamind.app.feature.library.components.FolderNoteRow
import com.novamind.app.feature.library.components.FolderRenameRow
import com.novamind.app.feature.library.components.FolderRow
import com.novamind.app.feature.library.components.LibraryNoteRow
import com.novamind.app.feature.library.components.SegmentedTabBar
import com.novamind.app.feature.library.components.TopIconButton
import com.novamind.app.feature.library.components.ViewModeToggle
import com.novamind.app.feature.library.components.sampleFolders
import com.novamind.app.feature.library.components.sampleNotes
import com.novamind.app.ui.components.BackButton
import com.novamind.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onCreateNote: () -> Unit = {},
    onToggleViewMode: () -> Unit = {},
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
        // 顶部工具条：左侧栏 / 搜索 / 创建
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                // 作为子页进入（如首页 See all）：左上角返回键，通用组件（与 Create 等页统一）
                BackButton(onClick = onBack, background = ColorIconBtn, tint = ColorTextTitle)
            } else {
                // 现状：侧栏入口 → 通知宿主打开抽屉
                TopIconButton(
                    iconRes = R.drawable.ic_panel_left,
                    desc = "Sidebar",
                    shape = RoundedCornerShape(12.dp),
                    onClick = onOpenSidebar,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopIconButton(R.drawable.ic_search, "Search", shape = CircleShape)
                TopIconButton(
                    iconRes = R.drawable.ic_nav_create,
                    desc = "Create",
                    shape = CircleShape,
                    // Folders 页 → 创建文件夹；Recent 页 → 新建笔记
                    onClick = {
                        if (pagerState.currentPage == 1) showCreateFolder = true else onCreateNote()
                    },
                )
            }
        }

        // 标题行：左侧 Library 标题，右侧视图切换按钮（仅 Recent 页显示）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Library",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ColorTextTitle,
            )
            // 仅 Recent 页可见，但始终占位（invisible 而非 gone），避免标题行间距跳动
            ViewModeToggle(
                viewMode = uiState.viewMode,
                onClick = onToggleViewMode,
                visible = pagerState.currentPage == 0,
            )
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
                )
                else -> FoldersPage(
                    folders = uiState.folders,
                    onOpenFolder = onOpenFolder,
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

/** Recent 页：无笔记显示空状态；有笔记按 viewMode 显示双列网格或单列列表。点击笔记进入预览/编辑页。 */
@Composable
private fun RecentPage(
    uiState: LibraryUiState,
    onCreateNote: () -> Unit,
    onOpenNote: (String) -> Unit,
) {
    when {
        uiState.notes.isEmpty() ->
            EmptyState(onCreateNote = onCreateNote, modifier = Modifier.fillMaxSize())

        uiState.viewMode == LibraryViewMode.GRID ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
            ) {
                items(uiState.notes, key = { it.id }) { note ->
                    LibraryNoteCard(note = note, onClick = { onOpenNote(note.id) })
                }
            }

        else ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
            ) {
                items(uiState.notes, key = { it.id }) { note ->
                    LibraryNoteRow(note = note, onClick = { onOpenNote(note.id) })
                }
            }
    }
}

/**
 * Folders 页：按文件夹聚合的列表，空则显示提示。
 * 支持长按某行拖拽排序；松手后通过 [onReorder] 回传新的名称顺序。
 */
@Composable
private fun FoldersPage(
    folders: List<LibraryFolder>,
    onOpenFolder: (String) -> Unit,
    onReorder: (List<String>) -> Unit = {},
    onRenameFolder: (old: String, new: String) -> Unit = { _, _ -> },
    onDeleteFolder: (String) -> Unit = {},
    onChangeFolderColor: (name: String, colorHex: String?) -> Unit = { _, _ -> },
) {
    // 重命名 / 删除 / 改色目标文件夹名（null = 不显示对应弹窗）
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var colorTarget by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    if (folders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No folders yet.",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = ColorTextSub,
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    // 拖拽状态：拖起项在列表中的索引、拖起时的布局信息、累计拖动距离
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var draggedDistance by remember { mutableFloatStateOf(0f) }
    var initialItemOffset by remember { mutableStateOf(0) }
    var initialItemSize by remember { mutableStateOf(0) }

    // 本地可变副本：单一稳定对象（pointerInput 闭包始终引用它）；非拖拽时从 folders 同步，
    // 拖拽中保持本地换序不被外部刷新打断。
    val items = remember { mutableStateListOf<LibraryFolder>() }
    LaunchedEffect(folders, draggingIndex) {
        if (draggingIndex == null) {
            items.clear()
            items.addAll(folders)
        }
    }

    fun reset() {
        draggingIndex = null
        draggedDistance = 0f
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }
                            ?.let { info ->
                                draggingIndex = info.index
                                initialItemOffset = info.offset
                                initialItemSize = info.size
                                draggedDistance = 0f
                                // 抬起时震动反馈（主流拖拽体验）
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val from = draggingIndex
                        if (from != null) {
                            draggedDistance += dragAmount.y
                            // 拖拽项当前中心（相对列表视口）
                            val draggedCenter = initialItemOffset + draggedDistance + initialItemSize / 2f
                            // 找到被中心覆盖、且不是自身的目标行 → 与之换序
                            val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                info.index != from &&
                                    draggedCenter.toInt() in info.offset..(info.offset + info.size)
                            }
                            if (target != null) {
                                items.add(target.index, items.removeAt(from))
                                draggingIndex = target.index
                            }
                        }
                    },
                    onDragEnd = {
                        if (draggingIndex != null) onReorder(items.map { it.name })
                        reset()
                    },
                    onDragCancel = { reset() },
                )
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
    ) {
        itemsIndexed(items, key = { _, it -> it.name }) { index, folder ->
            val isDragging = index == draggingIndex
            // 拖拽项：用 graphicsLayer 纯跟手（仅 translationY），不缩放、不改外观；不挂 animateItem，
            // 松手时移除 translation 直接定格、不触发布局动画。
            // 其余项始终挂 animateItem：拖拽中被挤开/交换时平滑过渡，避免突兀。
            val rowModifier = if (isDragging) {
                Modifier
                    .zIndex(1f)
                    .graphicsLayer {
                        // 用 key（文件夹名）定位拖拽项自身的实时偏移，避免换序那帧 index 错位导致跟手抖动
                        val current = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key == folder.name }?.offset ?: initialItemOffset
                        translationY = initialItemOffset + draggedDistance - current
                    }
            } else {
                Modifier.animateItem()
            }
            if (folder.name == renameTarget) {
                // 行内重命名：× 取消 + 输入框 + 绿色 ✓ 确认
                FolderRenameRow(
                    initialName = folder.name,
                    modifier = rowModifier,
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
                FolderRow(
                    folder = folder,
                    onClick = { onOpenFolder(folder.name) },
                    modifier = rowModifier,
                    onRename = { renameTarget = folder.name },
                    onChangeColor = { colorTarget = folder.name },
                    onDelete = { deleteTarget = folder.name },
                )
            }
        }
    }

    // 删除二次确认弹窗
    deleteTarget?.let { target ->
        DeleteFolderDialog(
            folderName = target,
            onConfirm = {
                onDeleteFolder(target)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
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
            // 抽屉宿主：点击左上角侧栏按钮或从左边缘右滑打开（仅底栏入口，非子页）
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            // 抽屉打开时通知宿主隐藏底部导航栏（盖住底栏）；离开时复位
            val drawerOpen = drawerState.targetValue == DrawerValue.Open
            LaunchedEffect(drawerOpen) { onFullscreenChange(drawerOpen) }
            DisposableEffect(Unit) { onDispose { onFullscreenChange(false) } }
            // 先打开目标页（全屏覆盖层滑入），待其盖住后再关抽屉，
            // 避免「抽屉关闭」与「新页滑入」同时进行造成动画断层。
            fun closeDrawerThen(action: () -> Unit) {
                action()
                scope.launch {
                    kotlinx.coroutines.delay(250)
                    drawerState.close()
                }
            }

            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = onBack == null,
                drawerContent = {
                    LibraryDrawer(
                        folders = uiState.folders,
                        onOpenFolder = { name -> closeDrawerThen { selectedFolder = name } },
                        onOpenTagManager = { closeDrawerThen(onOpenTagManager) },
                        onOpenSharedWithMe = { closeDrawerThen(onOpenSharedWithMe) },
                        onOpenRecycleBin = { closeDrawerThen(onOpenRecycleBin) },
                    )
                },
            ) {
                LibraryScreen(
                    uiState = uiState,
                    onCreateNote = onCreateNote,
                    onToggleViewMode = viewModel::toggleViewMode,
                    onOpenSidebar = { scope.launch { drawerState.open() } },
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

@Preview(showBackground = true, showSystemUi = true, name = "Folders 页")
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

@Preview(showBackground = true, name = "Library · RecentPage 网格")
@Composable
private fun RecentPageGridPreview() {
    AppTheme {
        RecentPage(
            uiState = LibraryUiState(notes = sampleNotes, viewMode = LibraryViewMode.GRID),
            onCreateNote = {},
            onOpenNote = {},
        )
    }
}

@Preview(showBackground = true, name = "Library · RecentPage 列表")
@Composable
private fun RecentPageListPreview() {
    AppTheme {
        RecentPage(
            uiState = LibraryUiState(notes = sampleNotes, viewMode = LibraryViewMode.LIST),
            onCreateNote = {},
            onOpenNote = {},
        )
    }
}
