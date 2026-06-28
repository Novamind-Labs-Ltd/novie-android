package com.novamind.app.feature.library

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.R
import com.novamind.app.ui.components.BackButton
import com.novamind.app.ui.theme.AppTheme
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
    onOpenNote: (String) -> Unit = {},
    onOpenTagManager: () -> Unit = {},
    onOpenSharedWithMe: () -> Unit = {},
    onOpenRecycleBin: () -> Unit = {},
    onBack: (() -> Unit)? = null,   // 非 null：左上角显示返回键并触发；null：保持现状（侧栏入口）
    modifier: Modifier = Modifier,
) {
    // 分段标签 + 内容：Recent / Folders 两页，支持左右滑动与点击切换
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    // 抽屉：点击左上角侧栏按钮、或从左边缘右滑打开
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    fun closeDrawerThen(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = onBack == null,   // 仅底栏入口（非子页）启用边缘右滑手势
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding()
            .padding(bottom = 100.dp),
    ) {
        // 顶部工具条：左侧栏 / 搜索 / 更多
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
                // 现状：侧栏入口 → 打开抽屉
                TopIconButton(
                    iconRes = R.drawable.ic_panel_left,
                    desc = "Sidebar",
                    shape = RoundedCornerShape(12.dp),
                    onClick = { scope.launch { drawerState.open() } },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopIconButton(R.drawable.ic_search, "Search", shape = CircleShape)
                TopIconButton(R.drawable.ic_more, "More", shape = CircleShape, bg = Color.Transparent)
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
            if (pagerState.currentPage == 0) {
                ViewModeToggle(viewMode = uiState.viewMode, onClick = onToggleViewMode)
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
                )
                else -> FoldersPage(folders = uiState.folders)
            }
        }
    }
    }
}

/**
 * 左侧抽屉：顶部为最近笔记（chevron + 标题），底部固定 Tag manager / Shared with me / Recycle Bin。
 * 宽度约屏宽 82%，白底；点击左上角按钮或从左边缘右滑打开。
 */
@Composable
private fun LibraryDrawer(
    notes: List<LibraryNote>,
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

/** Recent 页：无笔记显示空状态；有笔记按 viewMode 显示双列网格或单列列表。 */
@Composable
private fun RecentPage(uiState: LibraryUiState, onCreateNote: () -> Unit) {
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
                    LibraryNoteCard(note = note)
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
                    LibraryNoteRow(note = note)
                }
            }
    }
}

/**
 * 视图切换按钮：圆角方形白底按钮（区别于顶部圆形搜索按钮），
 * 图标随当前模式切换——网格态显示列表图标、列表态显示网格图标，提示「点击切到另一种」。
 */
@Composable
private fun ViewModeToggle(viewMode: LibraryViewMode, onClick: () -> Unit) {
    val isGrid = viewMode == LibraryViewMode.GRID
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ColorIconBtn)
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (isGrid) R.drawable.ic_format_list else R.drawable.ic_grid),
            contentDescription = if (isGrid) "Switch to list view" else "Switch to grid view",
            tint = ColorTextTitle,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 列表态笔记项：整宽横向卡片（标签 + 标题 + 预览）。 */
@Composable
private fun LibraryNoteRow(note: LibraryNote) {
    Surface(
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
                        text = note.tag,
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
                note.preview,
                fontSize = 13.sp,
                color = ColorTextSub,
                lineHeight = 18.sp,
                maxLines = 2,
            )
        }
    }
}

/** Folders 页：按文件夹聚合的列表，空则显示提示。 */
@Composable
private fun FoldersPage(folders: List<LibraryFolder>) {
    if (folders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No folders yet.",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = ColorTextSub,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
        ) {
            items(folders, key = { it.name }) { folder ->
                FolderRow(folder = folder)
            }
        }
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

@Composable
private fun FolderRow(folder: LibraryFolder) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ColorBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ColorAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    tint = ColorAccent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(folder.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ColorTextTitle)
                Text(
                    text = "${folder.noteCount} ${if (folder.noteCount == 1) "note" else "notes"}",
                    fontSize = 12.sp,
                    color = ColorTextSub,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = ColorTextSub,
                modifier = Modifier.size(18.dp),
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
private fun LibraryNoteCard(note: LibraryNote) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ColorBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = ColorAccent.copy(alpha = 0.1f),
            ) {
                Text(
                    text = note.tag,
                    fontSize = 10.sp,
                    color = ColorAccent,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Text(note.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
            Text(note.preview, fontSize = 12.sp, color = ColorTextSub, lineHeight = 17.sp, maxLines = 3)
        }
    }
}

@Composable
fun LibraryRoute(
    onCreateNote: () -> Unit = {},
    onBack: (() -> Unit)? = null,   // 非 null：作为子页进入，左上角为返回键
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(
        uiState = uiState,
        onCreateNote = onCreateNote,
        onToggleViewMode = viewModel::toggleViewMode,
        onBack = onBack,
        modifier = modifier,
    )
}

private val sampleNotes = listOf(
    LibraryNote("1", "Market research", "Overview of competitors in 2026.", "Research"),
    LibraryNote("2", "Q2 Strategy", "Key initiatives for Q2 growth plan.", "Strategy"),
    LibraryNote("3", "Design system", "Component tokens and guidelines.", "Design"),
    LibraryNote("4", "Team retro", "Sprint retrospective notes.", "Meeting"),
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
