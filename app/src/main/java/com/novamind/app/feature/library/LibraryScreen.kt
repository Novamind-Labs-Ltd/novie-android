package com.novamind.app.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.R
import com.novamind.app.feature.note.NoteItem
import com.novamind.app.ui.components.BackButton
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.ColorUtils
import com.novamind.app.util.TimeUtils
import kotlinx.coroutines.launch

private val BgPage = Color(0xFFF4F2EC)
private val ColorTextTitle = Color(0xFF1A1A1A)
private val ColorTextSub = Color(0xFF6B6B6B)
private val ColorBorder = Color(0xFFE3E0D8)
private val ColorAccent = Color(0xFF3D7A5A)
private val ColorIconBtn = Color(0xFFFFFFFF)

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
                )
            }
        }
    }

    // 创建文件夹弹层（Folders 页点击创建时）
    if (showCreateFolder) {
        CreateFolderSheet(
            onCreate = { name, colorHex ->
                showCreateFolder = false
                onCreateFolder(name, colorHex)
            },
            onDismiss = { showCreateFolder = false },
        )
    }
}

/**
 * 左侧抽屉：顶部为最近笔记（chevron + 标题），底部固定 Tag manager / Shared with me / Recycle Bin。
 * 宽度约屏宽 82%，白底；点击左上角按钮或从左边缘右滑打开。
 */
@Composable
private fun LibraryDrawer(
    notes: List<NoteItem>,
    onOpenNote: (String) -> Unit,
    onOpenTagManager: () -> Unit,
    onOpenSharedWithMe: () -> Unit,
    onOpenRecycleBin: () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = Color.White,
        drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
        modifier = Modifier.fillMaxWidth(0.82f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(vertical = 24.dp),
        ) {
            // 顶部：最近笔记（最多 8 条）
            notes.take(8).forEach { note ->
                DrawerNoteItem(title = note.title, onClick = { onOpenNote(note.id) })
            }

            Spacer(Modifier.weight(1f))

            HorizontalDivider(
                color = ColorBorder,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            DrawerActionItem(R.drawable.ic_tag, "Tag manager", onClick = onOpenTagManager)
            DrawerActionItem(R.drawable.ic_link, "Shared with me", onClick = onOpenSharedWithMe)
            DrawerActionItem(R.drawable.ic_delete, "Recycle Bin", onClick = onOpenRecycleBin)
        }
    }
}

/** 抽屉的最近笔记项：左侧 chevron + 标题。 */
@Composable
private fun DrawerNoteItem(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = ColorTextSub,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = title,
            fontSize = 16.sp,
            color = ColorTextTitle,
            maxLines = 1,
        )
    }
}

/** 抽屉底部操作项：图标 + 标签。 */
@Composable
private fun DrawerActionItem(iconRes: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = ColorTextTitle,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            fontSize = 16.sp,
            color = ColorTextTitle,
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
 * 视图切换按钮：圆角方形白底按钮（区别于顶部圆形搜索按钮），
 * 图标显示「当前」视图模式——网格态显示网格图标、列表态显示列表图标，点击切换。
 */
@Composable
private fun ViewModeToggle(viewMode: LibraryViewMode, onClick: () -> Unit, visible: Boolean = true) {
    val isGrid = viewMode == LibraryViewMode.GRID
    Box(
        modifier = Modifier
            .size(42.dp)
            // 不可见时仍保留占位（alpha 0），并禁用点击
            .alpha(if (visible) 1f else 0f)
            .clip(RoundedCornerShape(12.dp))
            .background(ColorIconBtn)
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
            .clickable(enabled = visible, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (isGrid) R.drawable.ic_grid else R.drawable.ic_format_list),
            contentDescription = if (isGrid) "Grid view, tap to switch to list" else "List view, tap to switch to grid",
            tint = ColorTextTitle,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 列表态笔记项：整宽横向卡片（标签 + 标题 + 预览）。 */
@Composable
private fun LibraryNoteRow(note: NoteItem, onClick: () -> Unit = {}) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ColorBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = RoundedCornerShape(50), color = ColorAccent.copy(alpha = 0.1f)) {
                    Text(
                        text = note.tags.firstOrNull() ?: note.folderName ?: "Note",
                        fontSize = 10.sp,
                        color = ColorAccent,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Text(
                    note.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextTitle,
                )
            }
            Text(
                note.description,
                fontSize = 13.sp,
                color = ColorTextSub,
                lineHeight = 18.sp,
                maxLines = 2,
            )
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
) {
    // 重命名目标文件夹名（null = 不显示重命名弹窗）
    var renameTarget by remember { mutableStateOf<String?>(null) }

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
    // 本地可变副本：拖拽过程中即时换序；folders 变化（计数/新增）时重置为最新
    var items by remember(folders) { mutableStateOf(folders) }

    // 拖拽状态：拖起项在列表中的索引、拖起时的布局信息、累计拖动距离
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var draggedDistance by remember { mutableFloatStateOf(0f) }
    var initialItemOffset by remember { mutableStateOf(0) }
    var initialItemSize by remember { mutableStateOf(0) }

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
                                items = items.toMutableList().apply { add(target.index, removeAt(from)) }
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
            // 拖拽项：跟手 translationY + 轻微放大 + 抬升阴影凸显；其余项用 animateItem 平滑归位
            val rowModifier = if (isDragging) {
                Modifier
                    .zIndex(1f)
                    .graphicsLayer {
                        val current = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == index }?.offset ?: initialItemOffset
                        translationY = initialItemOffset + draggedDistance - current
                        scaleX = 1.03f
                        scaleY = 1.03f
                    }
            } else {
                Modifier.animateItem()
            }
            FolderRow(
                folder = folder,
                onClick = { onOpenFolder(folder.name) },
                modifier = rowModifier,
                elevation = if (isDragging) 12.dp else 1.dp,
                onRename = { renameTarget = folder.name },
                onDelete = { onDeleteFolder(folder.name) },
                // Reorder：占位（拖拽排序用长按），菜单关闭即可
            )
        }
    }

    // 重命名弹窗
    renameTarget?.let { target ->
        RenameFolderDialog(
            initialName = target,
            onConfirm = { newName ->
                onRenameFolder(target, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
}

/** 重命名文件夹弹窗：预填当前名，确认回传新名。 */
@Composable
private fun RenameFolderDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialName) }
    val trimmed = text.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Rename folder", fontWeight = FontWeight.Bold, color = ColorTextTitle) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty() && trimmed != initialName,
            ) { Text("Rename", color = ColorAccent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = ColorTextSub) }
        },
    )
}

/**
 * 分段标签栏：两个等宽 Tab（Recent / Folders），底部一条浅色分隔线，
 * 黑色指示条随 [indicatorFraction] 在两 Tab 间平滑滑动。
 */
@Composable
private fun SegmentedTabBar(
    selectedIndex: Int,
    indicatorFraction: Float,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val tabWidth = maxWidth / 2
        val indicatorWidth = 76.dp
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                SegmentTab(
                    iconRes = R.drawable.ic_history,
                    label = "Recent",
                    selected = selectedIndex == 0,
                    onClick = { onTabClick(0) },
                    modifier = Modifier.weight(1f),
                )
                SegmentTab(
                    iconRes = R.drawable.ic_folder,
                    label = "Folders",
                    selected = selectedIndex == 1,
                    onClick = { onTabClick(1) },
                    modifier = Modifier.weight(1f),
                )
            }
            // 分隔线 + 滑动指示条叠放
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .align(Alignment.BottomStart)
                        .background(ColorBorder),
                )
                Box(
                    modifier = Modifier
                        .offset(x = tabWidth * indicatorFraction + (tabWidth - indicatorWidth) / 2)
                        .width(indicatorWidth)
                        .height(2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ColorTextTitle),
                )
            }
        }
    }
}

@Composable
private fun SegmentTab(
    iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (selected) ColorTextTitle else ColorTextSub,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) ColorTextTitle else ColorTextSub,
        )
    }
}

/** 无自定义色时：按文件夹名稳定地从色板（排除默认中性色）取一种颜色。 */
private fun folderAccentFor(name: String): Color {
    val palette = folderColorOptions.drop(1)   // 跳过首个「默认/中性」色
    if (palette.isEmpty()) return ColorAccent
    val idx = ((name.hashCode() % palette.size) + palette.size) % palette.size
    return palette[idx].icon
}

@Composable
private fun FolderRow(
    folder: LibraryFolder,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    elevation: androidx.compose.ui.unit.Dp = 1.dp,   // 拖拽态抬升以凸显
    onRename: () -> Unit = {},
    onReorder: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    // 文件夹颜色：有自定义色用之；否则按名称稳定地从色板取一种，使列表多彩且一致
    val accent = ColorUtils.parseHexColor(folder.colorHex) ?: folderAccentFor(folder.name)
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, ColorBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = elevation,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = folder.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = ColorTextTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 笔记数
            Text(
                text = folder.noteCount.toString(),
                fontSize = 14.sp,
                color = ColorTextSub,
            )
            // 更多：展开 Rename / Reorder / Delete
            Box {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { menuExpanded = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more),
                        contentDescription = "More",
                        tint = ColorTextSub,
                        modifier = Modifier.size(18.dp),
                    )
                }
                FolderActionsMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onRename = { menuExpanded = false; onRename() },
                    onReorder = { menuExpanded = false; onReorder() },
                    onDelete = { menuExpanded = false; onDelete() },
                )
            }
        }
    }
}

/** 文件夹「更多」下拉菜单：重命名 / 排序 / 删除。 */
@Composable
private fun FolderActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onReorder: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White,
        shadowElevation = 8.dp,
    ) {
        DropdownMenuItem(
            text = { Text("Rename", fontSize = 16.sp, color = ColorTextTitle) },
            onClick = onRename,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        )
        DropdownMenuItem(
            text = { Text("Reorder", fontSize = 16.sp, color = ColorTextTitle) },
            onClick = onReorder,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        )
        DropdownMenuItem(
            text = { Text("Delete", fontSize = 16.sp, color = Color(0xFFC8391A)) },
            onClick = onDelete,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
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

/** 文件夹详情页的笔记项：日期 + 标题 + 预览（整宽卡片）。 */
@Composable
private fun FolderNoteRow(note: NoteItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ColorBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = TimeUtils.smart(note.updatedAt),
                fontSize = 12.sp,
                color = ColorTextSub,
            )
            Text(note.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
            Text(
                note.description,
                fontSize = 14.sp,
                color = ColorTextTitle.copy(alpha = 0.8f),
                lineHeight = 20.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TopIconButton(
    iconRes: Int,
    desc: String,
    shape: androidx.compose.ui.graphics.Shape,
    bg: Color = ColorIconBtn,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = desc,
            tint = ColorTextTitle,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun EmptyState(onCreateNote: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyIllustration()
        Spacer(Modifier.height(28.dp))
        Text(
            text = "No notes yet.",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = ColorTextTitle,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Start a new note to organise your projects, tasks, or brainstorming sessions.",
            fontSize = 14.sp,
            color = ColorTextSub,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        // Create new note 按钮（黑色胶囊）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF111111))
                .clickable(onClick = onCreateNote)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_create),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Text("Create new note", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 空状态插图：叠放的笔记本/卡片 + 装饰圆点（纯 Canvas 绘制，无需图片资源）。 */
@Composable
private fun EmptyIllustration() {
    Canvas(modifier = Modifier.size(width = 176.dp, height = 140.dp)) {
        val w = size.width
        val h = size.height
        val center = Offset(w * 0.5f, h * 0.5f)

        // 底部柔和阴影
        drawOval(
            color = Color(0x12000000),
            topLeft = Offset(w * 0.18f, h * 0.82f),
            size = Size(w * 0.64f, h * 0.12f),
        )
        // 后面一本（向左倾斜）
        rotate(degrees = -10f, pivot = center) {
            drawRoundRect(
                color = Color(0xFFE7E3D8),
                topLeft = Offset(w * 0.24f, h * 0.20f),
                size = Size(w * 0.46f, h * 0.52f),
                cornerRadius = CornerRadius(10f, 10f),
            )
        }
        // 中间白本（轻微右倾）
        rotate(degrees = 5f, pivot = center) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.28f, h * 0.24f),
                size = Size(w * 0.44f, h * 0.52f),
                cornerRadius = CornerRadius(10f, 10f),
            )
            drawRoundRect(
                color = ColorBorder,
                topLeft = Offset(w * 0.28f, h * 0.24f),
                size = Size(w * 0.44f, h * 0.52f),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = 2f),
            )
        }
        // 前面：绿色书脊 + 白色页
        drawRoundRect(
            color = ColorAccent,
            topLeft = Offset(w * 0.30f, h * 0.40f),
            size = Size(w * 0.09f, h * 0.40f),
            cornerRadius = CornerRadius(6f, 6f),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.39f, h * 0.40f),
            size = Size(w * 0.30f, h * 0.40f),
            cornerRadius = CornerRadius(6f, 6f),
        )
        drawRoundRect(
            color = ColorBorder,
            topLeft = Offset(w * 0.39f, h * 0.40f),
            size = Size(w * 0.30f, h * 0.40f),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = 2f),
        )
        // 装饰圆点
        drawCircle(color = Color(0xFFCDC8BC), radius = w * 0.045f, center = Offset(w * 0.80f, h * 0.34f))
        drawCircle(color = ColorAccent.copy(alpha = 0.35f), radius = w * 0.018f, center = Offset(w * 0.20f, h * 0.30f))
        drawCircle(color = Color(0xFFCDC8BC), radius = w * 0.014f, center = Offset(w * 0.78f, h * 0.66f))
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
            fun closeDrawerThen(action: () -> Unit) {
                scope.launch { drawerState.close() }
                action()
            }

            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = onBack == null,
                drawerContent = {
                    LibraryDrawer(
                        notes = uiState.notes,
                        onOpenNote = { id -> closeDrawerThen { onOpenNote(id) } },
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
                    pagerState = pagerState,
                    onBack = onBack,
                    modifier = modifier,
                )
            }
        }
    }
}

private val sampleNotes = listOf(
    NoteItem("1", "Q3 KPIs", "Discussed Q3 KPIs. John to finalize the report by Thursday. Next meeting: Monday at 10 AM.", tags = listOf("Work"), folderName = "Work", updatedAt = System.currentTimeMillis()),
    NoteItem("2", "Team sync", "Team sync: Marketing launch on track. Felix leads HK event. RSVP for offsite by Friday.", tags = listOf("Work"), folderName = "Work", updatedAt = System.currentTimeMillis()),
    NoteItem("3", "Client call", "Client call notes: Requirements updated. Development starts Monday. QA testing scheduled for July.", tags = listOf("Work"), folderName = "Work", updatedAt = System.currentTimeMillis()),
    NoteItem("4", "Team retro", "Sprint retrospective notes.", tags = listOf("Personal"), folderName = "Personal", updatedAt = System.currentTimeMillis()),
)

private val sampleFolders = listOf(
    LibraryFolder("Work", 8),
    LibraryFolder("Projects", 5),
    LibraryFolder("Personal", 3),
    LibraryFolder("Unfiled", 2),
)

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
