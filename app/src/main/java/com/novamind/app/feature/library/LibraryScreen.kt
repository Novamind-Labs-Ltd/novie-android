package com.novamind.app.feature.library

import android.widget.Toast
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.R
import com.novamind.app.common.config.AppConfig
import com.novamind.app.feature.create.model.NoteItem
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.Palette
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.components.BackButton
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.ColorUtils
import com.novamind.app.util.ColorUtils.toHex
import com.novamind.app.util.TimeUtils
import kotlinx.coroutines.launch

// 配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析（不使用硬编码颜色）
private val BgPage: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
private val BgCard: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val ColorTextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val ColorTextSub: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
private val ColorBorder: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.default.current()
private val ColorAccent: Color
    @Composable @ReadOnlyComposable get() = IconColors.Brand.default.current()
private val ColorIconBtn: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()

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
        color = BgCard,
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

/** 修改文件夹颜色（底部弹层）：点击色板即应用。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangeFolderColorSheet(
    currentHex: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BgCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Folder colour",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ColorTextTitle,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppConfig.Folder.COLORS.forEach { color ->
                    val optionHex = color.toHex()
                    val selected = optionHex.equals(currentHex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.12f))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) ColorTextTitle else ColorBorder,
                                shape = CircleShape,
                            )
                            .clickable { onPick(optionHex) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_folder),
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 删除文件夹二次确认（底部弹层）：Delete（红色实心）/ Cancel（描边）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteFolderDialog(
    folderName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BgCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Delete $folderName folder?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ColorTextTitle,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "This will permanently delete the $folderName folder.",
                fontSize = 14.sp,
                color = ColorTextSub,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(8.dp))
            // Delete（红色实心胶囊）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(BackgroundColors.Error.default.current())
                    .clickable(onClick = onConfirm)
                    .height(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Delete", color = Palette.white, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            // Cancel（描边胶囊）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, ColorTextTitle, RoundedCornerShape(50))
                    .clickable(onClick = onDismiss)
                    .height(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Cancel", color = ColorTextTitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 行内重命名行：× 取消 + 自动聚焦输入框 + 绿色 ✓ 确认（回传非空且变化后的新名）。 */
@Composable
private fun FolderRenameRow(
    initialName: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 用 TextFieldValue 让初始光标停在文本末尾
    var value by remember(initialName) {
        mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length)))
    }
    val trimmed = value.text.trim()
    // 非空且与原名不同才可确认
    val canConfirm = trimmed.isNotEmpty() && trimmed != initialName
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun confirm() {
        if (canConfirm) onConfirm(trimmed)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // × 取消
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = "Cancel",
            tint = ColorTextTitle,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onCancel)
                .padding(2.dp),
        )
        // 输入框（白底圆角，自动聚焦）
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            color = BgCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorder),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = ColorTextTitle),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(ColorTextTitle),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
        // ✓ 确认（绿色）
        Icon(
            painter = painterResource(R.drawable.ic_check_circle),
            contentDescription = "Confirm",
            tint = if (canConfirm) ColorAccent else ColorTextSub,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(enabled = canConfirm) { confirm() },
        )
    }
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

/** 无自定义色时：按文件夹名稳定地从配置色板取一种颜色。 */
private fun folderAccentFor(name: String): Color {
    val palette = AppConfig.Folder.COLORS
    if (palette.isEmpty()) return Palette.forrest600
    val idx = ((name.hashCode() % palette.size) + palette.size) % palette.size
    return palette[idx]
}

@Composable
private fun FolderRow(
    folder: LibraryFolder,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    elevation: androidx.compose.ui.unit.Dp = 1.dp,   // 拖拽态抬升以凸显
    onRename: () -> Unit = {},
    onChangeColor: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    // 文件夹颜色：有自定义色用之；否则按名称稳定地从色板取一种，使列表多彩且一致
    val accent = ColorUtils.parseHexColor(folder.colorHex) ?: folderAccentFor(folder.name)
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            // 边框映射文件夹颜色
            .border(1.dp, accent, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        shadowElevation = elevation,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 点击图标 → 打开 Folder colour 弹窗（与「Change color」一致）
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f))
                    .clickable(onClick = onChangeColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_folder),
                    contentDescription = "Change colour",
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
                    onChangeColor = { menuExpanded = false; onChangeColor() },
                    onDelete = { menuExpanded = false; onDelete() },
                )
            }
        }
    }
}

/** 文件夹「更多」下拉菜单：重命名 / 改颜色 / 删除。 */
@Composable
private fun FolderActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onChangeColor: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = BgCard,
        shadowElevation = 8.dp,
    ) {
        DropdownMenuItem(
            text = { Text("Rename", fontSize = 16.sp, color = ColorTextTitle) },
            onClick = onRename,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        )
        DropdownMenuItem(
            text = { Text("Change color", fontSize = 16.sp, color = ColorTextTitle) },
            onClick = onChangeColor,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        )
        DropdownMenuItem(
            text = { Text("Delete", fontSize = 16.sp, color = IconColors.Error.default.current()) },
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
        color = BgCard,
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
                .background(BackgroundColors.Primary.default.current())
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
                    tint = TextColors.Inverse.default.current(),
                    modifier = Modifier.size(18.dp),
                )
                Text("Create new note", color = TextColors.Inverse.default.current(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 空状态插图：叠放的笔记本/卡片 + 装饰圆点（纯 Canvas 绘制，无需图片资源）。 */
@Composable
private fun EmptyIllustration() {
    // 在 composable 作用域内解析设计系统颜色，供下方 Canvas（非 composable 作用域）使用
    val border = ColorBorder
    val accent = ColorAccent
    val paper = BgCard
    val shadow = Palette.black0
    val backBook = Palette.sand550
    val dot = Palette.sand600
    Canvas(modifier = Modifier.size(width = 176.dp, height = 140.dp)) {
        val w = size.width
        val h = size.height
        val center = Offset(w * 0.5f, h * 0.5f)

        // 底部柔和阴影
        drawOval(
            color = shadow,
            topLeft = Offset(w * 0.18f, h * 0.82f),
            size = Size(w * 0.64f, h * 0.12f),
        )
        // 后面一本（向左倾斜）
        rotate(degrees = -10f, pivot = center) {
            drawRoundRect(
                color = backBook,
                topLeft = Offset(w * 0.24f, h * 0.20f),
                size = Size(w * 0.46f, h * 0.52f),
                cornerRadius = CornerRadius(10f, 10f),
            )
        }
        // 中间白本（轻微右倾）
        rotate(degrees = 5f, pivot = center) {
            drawRoundRect(
                color = paper,
                topLeft = Offset(w * 0.28f, h * 0.24f),
                size = Size(w * 0.44f, h * 0.52f),
                cornerRadius = CornerRadius(10f, 10f),
            )
            drawRoundRect(
                color = border,
                topLeft = Offset(w * 0.28f, h * 0.24f),
                size = Size(w * 0.44f, h * 0.52f),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = 2f),
            )
        }
        // 前面：绿色书脊 + 白色页
        drawRoundRect(
            color = accent,
            topLeft = Offset(w * 0.30f, h * 0.40f),
            size = Size(w * 0.09f, h * 0.40f),
            cornerRadius = CornerRadius(6f, 6f),
        )
        drawRoundRect(
            color = paper,
            topLeft = Offset(w * 0.39f, h * 0.40f),
            size = Size(w * 0.30f, h * 0.40f),
            cornerRadius = CornerRadius(6f, 6f),
        )
        drawRoundRect(
            color = border,
            topLeft = Offset(w * 0.39f, h * 0.40f),
            size = Size(w * 0.30f, h * 0.40f),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = 2f),
        )
        // 装饰圆点
        drawCircle(color = dot, radius = w * 0.045f, center = Offset(w * 0.80f, h * 0.34f))
        drawCircle(color = accent.copy(alpha = 0.35f), radius = w * 0.018f, center = Offset(w * 0.20f, h * 0.30f))
        drawCircle(color = dot, radius = w * 0.014f, center = Offset(w * 0.78f, h * 0.66f))
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
                    onChangeFolderColor = viewModel::changeFolderColor,
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
    LibraryFolder("Work", 8, colorHex = "#388E64"),
    LibraryFolder("Projects", 5, colorHex = "#FF8C00"),
    LibraryFolder("Personal", 3, colorHex = "#4A8292"),
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
