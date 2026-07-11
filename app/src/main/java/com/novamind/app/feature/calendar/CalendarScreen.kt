package com.novamind.app.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.common.config.AppConfig
import com.novamind.app.data.calendar.CalendarEvent
import com.novamind.app.data.calendar.CalendarEventType
import com.novamind.app.data.tasks.CalendarTask
import com.novamind.app.feature.calendar.components.AgendaSection
import com.novamind.app.feature.calendar.components.BgPage
import com.novamind.app.feature.calendar.components.CalendarIllustration
import com.novamind.app.feature.calendar.components.ColorOnPrimary
import com.novamind.app.feature.calendar.components.ColorPrimary
import com.novamind.app.feature.calendar.components.ColorPrimaryBg
import com.novamind.app.feature.calendar.components.ColorSurface
import com.novamind.app.feature.calendar.components.ColorTextError
import com.novamind.app.feature.calendar.components.ColorTextInverse
import com.novamind.app.feature.calendar.components.ColorTextSub
import com.novamind.app.feature.calendar.components.ColorTextTitle
import com.novamind.app.feature.calendar.components.DayCell
import com.novamind.app.feature.calendar.components.EventRow
import com.novamind.app.feature.calendar.components.MeetingBg
import com.novamind.app.feature.calendar.components.MeetingIcon
import com.novamind.app.feature.calendar.components.NavArrow
import com.novamind.app.feature.calendar.components.PillIcon
import com.novamind.app.feature.calendar.components.StatCard
import com.novamind.app.feature.calendar.components.TaskRow
import com.novamind.app.feature.calendar.components.TodoBg
import com.novamind.app.feature.calendar.components.TodoIcon
import com.novamind.app.ui.theme.AppTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val weekLetters = listOf("M", "T", "W", "T", "F", "S", "S")

// 周条 pager：足够大的页数模拟"无限"前后翻周，中间页为锚点周（本周）。
private const val WEEK_PAGE_COUNT = 20_000
private const val WEEK_INITIAL_PAGE = WEEK_PAGE_COUNT / 2

/**
 * 无状态 Calendar 屏幕：仅消费 [CalendarUiState] 并通过 [onEvent] 上报交互。
 * 授权后展示统计与时段事件，未授权时展示「连接 Google 日历」空状态卡片。
 * 视觉组件拆分在 [com.novamind.app.feature.calendar.components] 包下。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    uiState: CalendarUiState,
    onEvent: (CalendarUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedDate = uiState.selectedDate
    val monday = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val dayName = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val dateStr = selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))

    // 下拉刷新：仅已连接时真正触发拉取（未连接/游客态下 Refresh 为 no-op）。
    val pullState = rememberPullToRefreshState()
    // 刷新指示器随机配色：仅在「开始刷新」这一刻换色，避免每次重组闪烁。配色池见 AppConfig。
    var indicatorColor by remember { mutableStateOf(AppConfig.PullRefresh.INDICATOR_COLORS.first()) }
    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) indicatorColor = AppConfig.PullRefresh.INDICATOR_COLORS.random()
    }
    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = { onEvent(CalendarUiEvent.Refresh) },
        state = pullState,
        modifier = modifier
            .fillMaxSize()
            .background(BgPage),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = uiState.isLoading,
                color = indicatorColor,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        // 左右滑动只在周条（DayCell 区域）内生效：由周条自身的 HorizontalPager 处理，
        // 内容区不挂全局横滑手势，统计卡/议程等区域滑动不切换日期。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp),
        ) {
        // 顶部标题 + 操作 pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Calendar", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = ColorTextTitle)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 「Today」：非今天时显示，点击立刻回到今天（周条 pager 会自动跟随）。
                if (selectedDate != LocalDate.now()) {
                    Surface(shape = RoundedCornerShape(50), color = ColorSurface, shadowElevation = 1.dp) {
                        Text(
                            text = "Today",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ColorPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onEvent(CalendarUiEvent.DateSelected(LocalDate.now())) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
                Surface(shape = RoundedCornerShape(50), color = ColorSurface, shadowElevation = 1.dp) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PillIcon(R.drawable.ic_add, "Add") { onEvent(CalendarUiEvent.AddTaskClicked) }
                        PillIcon(R.drawable.ic_search, "Search")
                        PillIcon(R.drawable.ic_more, "More") { onEvent(CalendarUiEvent.Refresh) }
                    }
                }
            }
        }

        // 日期导航：‹ 周四 / 18 June 2026 ›
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavArrow(left = true) { onEvent(CalendarUiEvent.PrevDay) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dayName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
                Text(dateStr, fontSize = 13.sp, color = ColorTextSub)
            }
            NavArrow(left = false) { onEvent(CalendarUiEvent.NextDay) }
        }

        Spacer(Modifier.height(12.dp))

        // 周条：HorizontalPager 支持左右滑动切周。
        // 锚点 = 本周周一，页号 = 锚点周 ± 偏移；只在 Screen 内换算，周切换仍通过 DateSelected 上报。
        val anchorMonday = remember { LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        val weekPage = WEEK_INITIAL_PAGE + ChronoUnit.WEEKS.between(anchorMonday, monday).toInt()
        val weekPagerState = rememberPagerState(initialPage = weekPage) { WEEK_PAGE_COUNT }

        // 滑动翻页停定 → 切换选中日期（保持星期几不变）。初始与同周停定为 no-op。
        LaunchedEffect(weekPagerState.settledPage) {
            val newMonday = anchorMonday.plusWeeks((weekPagerState.settledPage - WEEK_INITIAL_PAGE).toLong())
            if (newMonday != monday) {
                onEvent(
                    CalendarUiEvent.DateSelected(
                        newMonday.plusDays((selectedDate.dayOfWeek.value - 1).toLong()),
                    ),
                )
            }
        }
        // 外部改日期（箭头/点日期）跨周时，pager 动画跟随；滑动中不打断手势。
        LaunchedEffect(weekPage) {
            if (weekPagerState.currentPage != weekPage && !weekPagerState.isScrollInProgress) {
                weekPagerState.animateScrollToPage(weekPage)
            }
        }

        HorizontalPager(
            state = weekPagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val pageMonday = anchorMonday.plusWeeks((page - WEEK_INITIAL_PAGE).toLong())
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                (0..6).forEach { i ->
                    val date = pageMonday.plusDays(i.toLong())
                    DayCell(
                        letter = weekLetters[i],
                        day = date.dayOfMonth,
                        selected = date == selectedDate,
                        weekend = i >= 5,
                        modifier = Modifier.weight(1f),
                        onClick = { onEvent(CalendarUiEvent.DateSelected(date)) },
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // 统计卡片（游客拦截态不展示）
        if (!uiState.loginRequired) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    uiState.eventCount.toString(), "Events", MeetingBg, R.drawable.ic_nav_calendar, MeetingIcon,
                    Modifier.weight(1f),
                    selected = uiState.filter == AgendaFilter.EVENTS,
                    onClick = { onEvent(CalendarUiEvent.SelectAgendaFilter(AgendaFilter.EVENTS)) },
                )
                StatCard(
                    uiState.taskCount.toString(), "Tasks", TodoBg, R.drawable.ic_check_circle, TodoIcon,
                    Modifier.weight(1f),
                    selected = uiState.filter == AgendaFilter.TASKS,
                    onClick = { onEvent(CalendarUiEvent.SelectAgendaFilter(AgendaFilter.TASKS)) },
                )
            }
        }

        Spacer(Modifier.height(if (uiState.isConnected) 12.dp else 24.dp))

        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                fontSize = 13.sp,
                color = ColorTextError,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        // 游客（免登录）：不展示日历，提示登录后使用。
        if (uiState.loginRequired) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CalendarIllustration()
                Spacer(Modifier.height(20.dp))
                Text("Sign in to use Calendar", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Calendar is unavailable in guest mode. Sign in with your account to connect and sync events.",
                    fontSize = 14.sp,
                    color = ColorTextSub,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(20.dp))
        } else if (!uiState.isConnected) {
            // 未连接：连接 Google 日历空状态
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CalendarIllustration()
                Spacer(Modifier.height(20.dp))
                Text("Connect to Google Calendar", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Link your account to sync your events and keep your calendar up-to-date.",
                    fontSize = 14.sp,
                    color = ColorTextSub,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(ColorPrimaryBg)
                        .clickable { onEvent(CalendarUiEvent.Connect) }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_nav_calendar), null, tint = ColorOnPrimary, modifier = Modifier.size(18.dp))
                        Text("Connect to google calendar", color = ColorTextInverse, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // 议程：活动、任务分两个区块（各带小标题）；按 filter 决定显示哪块（游客拦截态不展示）。
        // 外层已无全局横滑手势，议程区天然不响应左右滑动；任务行右滑完成不受影响。
        if (!uiState.loginRequired && uiState.isConnected) {
            if (uiState.showEventsSection) {
                AgendaSection(
                    label = "Events",
                    count = uiState.eventCount,
                    empty = uiState.events.isEmpty(),
                    emptyText = "No events",
                    loading = uiState.isLoading,
                ) {
                    uiState.events.forEach { EventRow(it) }
                }
            }
            if (uiState.showTasksSection) {
                AgendaSection(
                    label = "Tasks",
                    count = uiState.taskCount,
                    empty = uiState.tasks.isEmpty(),
                    emptyText = "No tasks",
                    loading = uiState.isLoading,
                ) {
                    uiState.tasks.forEach { task ->
                        TaskRow(
                            task = task,
                            onComplete = { onEvent(CalendarUiEvent.SetTaskCompleted(it, completed = true)) },
                            onClick = { onEvent(CalendarUiEvent.TaskClicked(it)) },
                        )
                    }
                }
            }
        }
        }
    }
}

// ── Preview 样例数据 ──

private fun previewEvents(day: LocalDate) = listOf(
    // 已结束事件（昨天）：预览中应显示置灰 + 删除线。
    CalendarEvent("0", "Morning sync", false, day.minusDays(1).atTime(9, 30), day.minusDays(1).atTime(10, 0), "Meet", CalendarEventType.DEFAULT),
    CalendarEvent("1", "Team standup", false, day.atTime(9, 30), day.atTime(10, 0), "Meet", CalendarEventType.DEFAULT, isMeeting = true),
    CalendarEvent("2", "Focus: write spec", false, day.atTime(11, 0), day.atTime(12, 0), null, CalendarEventType.FOCUS_TIME),
    CalendarEvent("3", "Design review", false, day.atTime(14, 0), day.atTime(15, 0), "Room A", CalendarEventType.DEFAULT),
)

private fun previewTasks(day: LocalDate) = listOf(
    CalendarTask("t1", "list1", "Submit expense report", day, isCompleted = false, notes = null),
    CalendarTask("t2", "list1", "Reply to Alice", day, isCompleted = true, notes = null),
)

// ── 正常状态 ──

@Preview(showBackground = true, showSystemUi = true, name = "Calendar · Connected")
@Composable
private fun CalendarScreenConnectedPreview() {
    val day = LocalDate.now()
    AppTheme {
        CalendarScreen(
            uiState = CalendarUiState(
                connectionStatus = CalendarConnectionStatus.CONNECTED,
                events = previewEvents(day),
                tasks = previewTasks(day),
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Calendar · Connected · Empty agenda")
@Composable
private fun CalendarScreenEmptyAgendaPreview() {
    AppTheme {
        CalendarScreen(
            uiState = CalendarUiState(connectionStatus = CalendarConnectionStatus.CONNECTED),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Calendar · Tasks filter only")
@Composable
private fun CalendarScreenTasksFilterPreview() {
    val day = LocalDate.now()
    AppTheme {
        CalendarScreen(
            uiState = CalendarUiState(
                connectionStatus = CalendarConnectionStatus.CONNECTED,
                events = previewEvents(day),
                tasks = previewTasks(day),
                filter = AgendaFilter.TASKS,
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Calendar · Syncing (cached)")
@Composable
private fun CalendarScreenSyncingPreview() {
    val day = LocalDate.now()
    AppTheme {
        CalendarScreen(
            uiState = CalendarUiState(
                connectionStatus = CalendarConnectionStatus.SYNCING,
                events = previewEvents(day),
            ),
            onEvent = {},
        )
    }
}

// ── 未连接 / 拦截状态 ──

@Preview(showBackground = true, showSystemUi = true, name = "Calendar · Not connected")
@Composable
private fun CalendarScreenDisconnectedPreview() {
    AppTheme {
        CalendarScreen(
            uiState = CalendarUiState(connectionStatus = CalendarConnectionStatus.NOT_CONNECTED),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Calendar · Guest blocked")
@Composable
private fun CalendarScreenLoginRequiredPreview() {
    AppTheme {
        CalendarScreen(
            uiState = CalendarUiState(connectionStatus = CalendarConnectionStatus.LOGIN_REQUIRED),
            onEvent = {},
        )
    }
}

// ── 异常状态 ──

@Preview(showBackground = true, showSystemUi = true, name = "Calendar · Sync failed (retryable)")
@Composable
private fun CalendarScreenSyncFailedPreview() {
    val day = LocalDate.now()
    AppTheme {
        CalendarScreen(
            uiState = CalendarUiState(
                connectionStatus = CalendarConnectionStatus.SYNC_FAILED,
                events = previewEvents(day),
                errorMessage = "Failed to load calendar",
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Calendar · Authorization revoked")
@Composable
private fun CalendarScreenRevokedPreview() {
    AppTheme {
        CalendarScreen(
            uiState = CalendarUiState(
                connectionStatus = CalendarConnectionStatus.PERMISSION_REVOKED,
                errorMessage = "Google authorization expired, please reconnect",
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Calendar · Token expired (silently refreshing)")
@Composable
private fun CalendarScreenTokenExpiredPreview() {
    val day = LocalDate.now()
    AppTheme {
        CalendarScreen(
            uiState = CalendarUiState(
                connectionStatus = CalendarConnectionStatus.TOKEN_EXPIRED,
                events = previewEvents(day),
                tasks = previewTasks(day),
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Calendar · Action failed message (connected)")
@Composable
private fun CalendarScreenActionErrorPreview() {
    val day = LocalDate.now()
    AppTheme {
        CalendarScreen(
            uiState = CalendarUiState(
                connectionStatus = CalendarConnectionStatus.CONNECTED,
                events = previewEvents(day),
                tasks = previewTasks(day),
                errorMessage = "Couldn't complete the task. Please retry.",
            ),
            onEvent = {},
        )
    }
}
