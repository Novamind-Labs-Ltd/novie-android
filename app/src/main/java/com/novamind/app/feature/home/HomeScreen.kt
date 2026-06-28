package com.novamind.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.note.NoteItem
import com.novamind.app.ui.theme.AppTheme

// ─── 颜色 ─────────────────────────────────────────────────────────────────────

private val BgPage = Color(0xFFF0EFEA)
private val BgCard = Color(0xFFFFFFFF)
private val BgActionBar = Color(0xFFFFFFFF)
private val ColorTextTitle = Color(0xFF1A1A1A)
private val ColorTextSub = Color(0xFF6B6B6B)
private val ColorTextHint = Color(0xFFAAAAAA)

// ─── 顶部「更多」底部弹窗菜单 ──────────────────────────────────────────────────

/** 菜单分组。 */
enum class HomeMenuSection(val title: String) {
    Settings("Settings"),
    Support("Support"),
    About("About"),
}

enum class HomeMenuItem(
    val label: String,
    val iconRes: Int,
    val section: HomeMenuSection,
) {
    NotificationPreferences("Notification preferences", R.drawable.ic_notification, HomeMenuSection.Settings),
    Connectors("Connectors", R.drawable.ic_link, HomeMenuSection.Settings),
    Permissions("Permissions", R.drawable.ic_key, HomeMenuSection.Settings),
    AppLock("App lock", R.drawable.ic_lock, HomeMenuSection.Settings),
    HelpCentre("Help centre", R.drawable.ic_info, HomeMenuSection.Support),
    SendFeedback("Send feedback", R.drawable.ic_chat, HomeMenuSection.Support),
    ReportBug("Report a bug", R.drawable.ic_warning, HomeMenuSection.Support),
    AboutMyNovie("About MyNovie", R.drawable.ic_nav_brand, HomeMenuSection.About),
}

// ─── 无状态 Screen ────────────────────────────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onSearchQueryChange: (String) -> Unit,
    onUpcomingSeeAll: () -> Unit,
    onNotesSeeAll: () -> Unit,
    onNoteClick: (noteId: String) -> Unit = {},
    onMenuAction: (HomeMenuItem) -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onAvatarClick: () -> Unit = {},
    onRefresh: () -> Unit = {},
    avatarPath: String? = null,
    notificationCount: Int = 0,
    forceMenuOpen: Boolean = false,   // 预览用：默认展开「更多」菜单
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }

    // 下拉刷新：每次刷新随机换一个指示器颜色
    val pullState = rememberPullToRefreshState()
    var indicatorColor by remember { mutableStateOf(randomVividColor()) }
    LaunchedEffect(uiState.isRefreshing) {
        if (uiState.isRefreshing) indicatorColor = randomVividColor()
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier
            .fillMaxSize()
            .background(BgPage),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = uiState.isRefreshing,
                color = indicatorColor,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(bottom = 100.dp),
        ) {
            // ── 顶部栏 ──────────────────────────────────────────────────────
            TopBar(
                onMenuAction = onMenuAction,
                onNotificationsClick = onNotificationsClick,
                onAvatarClick = onAvatarClick,
                avatarPath = avatarPath,
                notificationCount = notificationCount,
                initialMenuExpanded = forceMenuOpen,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )

            // ── 标题 ─────────────────────────────────────────────────────────
            Text(
                text = "Home",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ColorTextTitle,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── 搜索框 ───────────────────────────────────────────────────────
            SearchBar(
                query = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    onSearchQueryChange(it)
                },
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Upcoming ─────────────────────────────────────────────────────
            SectionHeader(
                title = "Upcoming",
                onSeeAll = onUpcomingSeeAll,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                uiState.upcomingItems.forEach { item ->
                    UpcomingCard(item = item)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── My notes ─────────────────────────────────────────────────────
            SectionHeader(
                title = "My notes",
                onSeeAll = onNotesSeeAll,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.notes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "还没有笔记，去 Create 写一篇吧 ✍️",
                        fontSize = 13.sp,
                        color = ColorTextHint,
                    )
                }
            } else {
                // ── Note 卡片横向懒加载列表 ──────────────────────────────────
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = uiState.notes, key = { it.id }) { note ->
                        NoteCard(note = note, onClick = { onNoteClick(note.id) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ─── 顶部栏 ───────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    onMenuAction: (HomeMenuItem) -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onAvatarClick: () -> Unit = {},
    avatarPath: String? = null,
    notificationCount: Int = 0,
    initialMenuExpanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(initialMenuExpanded) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFFD0C8B8))
                .clickable(onClick = onAvatarClick),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarPath != null) {
                AsyncImage(
                    model = java.io.File(avatarPath),
                    contentDescription = "Profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                )
            } else {
                Text(text = "👤", fontSize = 22.sp)
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = BgActionBar,
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ── 通知按钮 + 右上角红色数量角标 ──────────────────────
                Box {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_notification),
                        contentDescription = "Notifications",
                        tint = ColorTextTitle,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onNotificationsClick),
                    )
                    if (notificationCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 3.dp, y = (-2).dp)
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD13C3C)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (notificationCount > 9) "9+" else "$notificationCount",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 8.sp,
                            )
                        }
                    }
                }
                // ── 更多按钮 + 底部弹窗菜单 ──────────────────────────────
                Icon(
                    painter = painterResource(id = R.drawable.ic_more),
                    contentDescription = "More",
                    tint = ColorTextTitle,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable { menuExpanded = true },
                )
                if (menuExpanded) {
                    MoreSheet(
                        onDismiss = { menuExpanded = false },
                        onItemClick = {
                            menuExpanded = false
                            onMenuAction(it)
                        },
                    )
                }
            }
        }
    }
}

// ─── 「更多」下拉菜单 ──────────────────────────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(
    onDismiss: () -> Unit,
    onItemClick: (HomeMenuItem) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // 直接展开到内容高度，不需要用户上拉（跳过半展开态）
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BgCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        MoreSheetContent(onItemClick = onItemClick)
    }
}

/** 「更多」菜单内容（与 ModalBottomSheet 解耦，便于 @Preview 直接预览）。 */
@Composable
private fun MoreSheetContent(onItemClick: (HomeMenuItem) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
    ) {
        HomeMenuSection.entries.forEach { section ->
            Text(
                text = section.title,
                fontSize = 12.sp,
                color = ColorTextHint,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
            )
            HomeMenuItem.entries.filter { it.section == section }.forEach { item ->
                MoreSheetRow(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun MoreSheetRow(item: HomeMenuItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(id = item.iconRes),
            contentDescription = null,
            tint = ColorTextTitle,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = item.label,
            fontSize = 16.sp,
            color = ColorTextTitle,
        )
    }
}

// ─── 搜索框 ───────────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = BgCard,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_search),
                contentDescription = null,
                tint = ColorTextHint,
                modifier = Modifier.size(20.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Type to Search...",
                            color = ColorTextHint,
                            fontSize = 15.sp,
                        )
                    }
                    inner()
                },
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = ColorTextTitle,
                )
            )
        }
    }
}

// ─── Section 标题行 ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = ColorTextTitle,
        )
        IconButton(onClick = onSeeAll, modifier = Modifier.size(28.dp)) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = "See all",
                tint = ColorTextSub,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ─── Upcoming 卡片 ────────────────────────────────────────────────────────────

@Composable
private fun UpcomingCard(
    item: UpcomingItem,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(id = item.iconResId),
                contentDescription = null,
                tint = Color(0xFF9E8E78),
                modifier = Modifier.size(36.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorTextTitle,
                )
                Text(
                    text = item.subtitle,
                    fontSize = 13.sp,
                    color = ColorTextSub,
                )
            }
        }
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    AppTheme {
        HomeScreen(
            uiState = HomeUiState(
                upcomingItems = listOf(
                    UpcomingItem("1", "Monthly report sharing", "Team project progress tracking", R.drawable.ic_upcoming_report),
                    UpcomingItem("2", "Board meeting", "Internal stakeholder alignment", R.drawable.ic_upcoming_meeting),
                ),
                notes = listOf(
                    NoteItem("1", "Market research", "Here is an overview of your competitors in 2026."),
                    NoteItem("2", "Market research", "Here is an overview of your competitors in 2026.", isSelected = true),
                    NoteItem("3", "Market research", "Here is an overview of your competitors in 2026."),
                ),
            ),
            onSearchQueryChange = {},
            onUpcomingSeeAll = {},
            onNotesSeeAll = {},
            // 不在预览里强开「更多」：ModalBottomSheet 无法在 @Preview 渲染，会让预览失效
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "More 菜单内容")
@Composable
private fun MoreSheetContentPreview() {
    AppTheme {
        MoreSheetContent(onItemClick = {})
    }
}

// 随机鲜明颜色（下拉刷新指示器用）
private fun randomVividColor(): Color =
    Color.hsv(
        hue = kotlin.random.Random.nextInt(0, 360).toFloat(),
        saturation = 0.75f,
        value = 0.85f,
    )
