package com.novamind.app.feature.home

import com.novamind.app.common.log.AppLog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.home.components.AskNovieButton
import com.novamind.app.feature.home.components.BgPage
import com.novamind.app.feature.home.components.ColorTextHint
import com.novamind.app.feature.home.components.HomeTopBar
import com.novamind.app.feature.home.components.RecentNoteCard
import com.novamind.app.feature.home.components.SectionHeader
import com.novamind.app.feature.home.components.UpcomingCard
import com.novamind.app.feature.create.model.NoteItem
import com.novamind.app.ui.components.AppPullToRefresh
import com.novamind.app.ui.theme.AppTheme

// 配色与视觉组件在 feature/home/components 包（HomeColors 等），本文件只保留编排与菜单模型。

private const val TAG = "Home"

// ─── 「更多」菜单模型（保留供 MoreSheet 复用；home_final 顶栏已不直接展示） ────────

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
    onUpcomingSeeAll: () -> Unit,
    onNotesSeeAll: () -> Unit,
    onNoteClick: (noteId: String) -> Unit = {},
    onTaskClick: (itemId: String) -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onAvatarClick: () -> Unit = {},
    onAskNovie: () -> Unit = {},
    onStartNotes: () -> Unit = {},
    onRefresh: () -> Unit = {},
    userName: String = "",
    avatarPath: String? = null,
    notificationCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding(),
    ) {
        // ── 固定头部：头像 + 问候 + 提醒（不随下拉/滚动移动）─────────────────
        HomeTopBar(
            userName = userName,
            onNotificationsClick = onNotificationsClick,
            onAvatarClick = onAvatarClick,
            avatarPath = avatarPath,
            notificationCount = notificationCount,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
        )

        // ── 自定义下拉刷新（非系统 PullToRefreshBox）：头部固定；下拉时内容整体下移，
        //     spinner 与内容平级、显示在让出的空白带中 ─────────────────────────
        AppPullToRefresh(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 120.dp),
            ) {
                Spacer(modifier = Modifier.height(8.dp))

            // ── Up next ──────────────────────────────────────────────────────
            SectionHeader(
                title = "Up next",
                onSeeAll = onUpcomingSeeAll,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 今日会议/任务；无数据显示空状态
            if (uiState.upcomingItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No meetings or tasks today",
                        fontSize = 13.sp,
                        color = ColorTextHint,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // 会议卡带「Start notes」按钮；任务卡整卡可点，进入任务详情
                    uiState.upcomingItems.forEach { item ->
                        UpcomingCard(
                            item = item,
                            showAction = item.isMeeting,
                            onAction = onStartNotes,
                            onClick = if (!item.isMeeting) {
                                { onTaskClick(item.id) }
                            } else null,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Ask Novie ────────────────────────────────────────────────────
            AskNovieButton(
                onClick = onAskNovie,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Recent notes ─────────────────────────────────────────────────
            SectionHeader(
                title = "Recent notes",
                onSeeAll = onNotesSeeAll,
                modifier = Modifier.padding(horizontal = 24.dp),
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
                        text = "No notes yet, head to Create to write one ✍️",
                        fontSize = 13.sp,
                        color = ColorTextHint,
                    )
                }
            } else {
                // ── Note 纵向卡片列表 ────────────────────────────────────────
                // 整页已在 verticalScroll 中，多条笔记直接纵向堆叠、随页面滚动
                // （不用 LazyColumn，避免与外层竖向滚动的无限高度约束冲突）。
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    uiState.notes.forEach { note ->
                        RecentNoteCard(
                            note = note,
                            onClick = {
                                AppLog.d(TAG) { "note clicked: id=${note.id} title=\"${note.title}\" " +
                                        "tags=${note.tags} folder=${note.folderName} " +
                                        "updatedAt=${note.updatedAt} hasImage=${note.imagePath != null}" }
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
}

// ─── Preview ──────────────────────────────────────────────────────────────────

// 预览示例数据（避免各 Preview 重复构造）。
private fun previewUpcoming(): List<UpcomingItem> = listOf(
    // 会议卡（带 Start notes）
    UpcomingItem("1", "Monthly report sharing", "Team project progress tracking", R.drawable.ic_upcoming_meeting, time = "10:00", isMeeting = true),
    UpcomingItem("2", "Board meeting", "Internal stakeholder alignment", R.drawable.ic_upcoming_meeting, time = "11:30", isMeeting = true),
    // 任务卡（不带按钮，Google Tasks 无具体时间）
    UpcomingItem("3", "Finalize Q3 report", "", R.drawable.ic_upcoming_report, time = "", isMeeting = false),
)

private fun previewNotes(count: Int): List<NoteItem> {
    val now = System.currentTimeMillis()
    val samples = listOf(
        NoteItem("1", "Q3 KPIs", "Discussed Q3 KPIs. John to finalize the report by Thursday.", updatedAt = now),
        NoteItem("2", "", "Discussed Q3 KPIs. John to finalize the report by Thursday.", updatedAt = now - 3_600_000L),
        NoteItem("3", "Pic notes", "Team offsite venue shortlist and travel logistics.", updatedAt = now - 86_400_000L),
        NoteItem("4", "Market research", "Here is an overview of your competitors in 2026.", updatedAt = now - 172_800_000L),
        NoteItem("5", "1:1 with Felix", "Hero campaign planning and Q4 goals.", updatedAt = now - 259_200_000L),
        NoteItem("6", "Weekly sync", "Action items and blockers from the team standup.", updatedAt = now - 345_600_000L),
    )
    return List(count) { i -> samples[i % samples.size].let { it.copy(id = "n$i", updatedAt = it.updatedAt) } }
}

@Preview(showBackground = true, showSystemUi = true, name = "Home · Success")
@Composable
private fun HomeScreenSuccessPreview() {
    AppTheme {
        HomeScreen(
            uiState = HomeUiState(upcomingItems = previewUpcoming(), notes = previewNotes(3)),
            onUpcomingSeeAll = {},
            onNotesSeeAll = {},
            userName = "Jam",
            notificationCount = 3,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Home · Many notes (scroll)")
@Composable
private fun HomeScreenManyNotesPreview() {
    AppTheme {
        HomeScreen(
            uiState = HomeUiState(upcomingItems = previewUpcoming(), notes = previewNotes(6)),
            onUpcomingSeeAll = {},
            onNotesSeeAll = {},
            userName = "Jam",
            notificationCount = 12,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Home · Empty (up next & notes)")
@Composable
private fun HomeScreenEmptyPreview() {
    AppTheme {
        HomeScreen(
            uiState = HomeUiState(upcomingItems = emptyList(), notes = emptyList()),
            onUpcomingSeeAll = {},
            onNotesSeeAll = {},
            userName = "Jam",
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Home · Loading (refresh)")
@Composable
private fun HomeScreenLoadingPreview() {
    AppTheme {
        HomeScreen(
            uiState = HomeUiState(upcomingItems = previewUpcoming(), notes = previewNotes(2), isRefreshing = true),
            onUpcomingSeeAll = {},
            onNotesSeeAll = {},
            userName = "Jam",
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Home · Guest")
@Composable
private fun HomeScreenGuestPreview() {
    AppTheme {
        HomeScreen(
            uiState = HomeUiState(upcomingItems = previewUpcoming(), notes = previewNotes(2)),
            onUpcomingSeeAll = {},
            onNotesSeeAll = {},
            userName = "",
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Home · Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenDarkPreview() {
    AppTheme(darkTheme = true) {
        HomeScreen(
            uiState = HomeUiState(upcomingItems = previewUpcoming(), notes = previewNotes(3)),
            onUpcomingSeeAll = {},
            onNotesSeeAll = {},
            userName = "Jam",
            notificationCount = 3,
        )
    }
}
