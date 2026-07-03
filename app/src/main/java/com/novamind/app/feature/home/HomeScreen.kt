package com.novamind.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.home.components.BgPage
import com.novamind.app.feature.home.components.ColorTextHint
import com.novamind.app.feature.home.components.ColorTextTitle
import com.novamind.app.feature.home.components.HomeTopBar
import com.novamind.app.feature.home.components.NoteCard
import com.novamind.app.feature.home.components.SearchBar
import com.novamind.app.feature.home.components.SectionHeader
import com.novamind.app.feature.home.components.UpcomingCard
import com.novamind.app.common.config.AppConfig
import com.novamind.app.common.log.DebugLog
import com.novamind.app.feature.note.NoteItem
import com.novamind.app.ui.theme.AppTheme

// 配色与视觉组件在 feature/home/components 包（HomeColors 等），本文件只保留编排与菜单模型。

private const val TAG = "Home"

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

    // 下拉刷新：每次开始刷新时从共享配色池随机换一个指示器颜色（配色池见 AppConfig）。
    val pullState = rememberPullToRefreshState()
    var indicatorColor by remember { mutableStateOf(AppConfig.PullRefresh.INDICATOR_COLORS.first()) }
    LaunchedEffect(uiState.isRefreshing) {
        if (uiState.isRefreshing) indicatorColor = AppConfig.PullRefresh.INDICATOR_COLORS.random()
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
            HomeTopBar(
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
                        NoteCard(
                            note = note,
                            onClick = {
                                DebugLog.d(
                                    TAG,
                                    "note clicked: id=${note.id} title=\"${note.title}\" " +
                                        "tags=${note.tags} folder=${note.folderName} " +
                                        "updatedAt=${note.updatedAt} hasImage=${note.imagePath != null}",
                                )
                                onNoteClick(note.id)
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
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
