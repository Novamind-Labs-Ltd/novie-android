package com.novamind.app.feature.calendar

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
import com.novamind.app.data.calendar.CalendarEvent
import com.novamind.app.data.calendar.CalendarEventType
import com.novamind.app.data.tasks.CalendarTask
import com.novamind.app.feature.calendar.components.AgendaSection
import com.novamind.app.feature.calendar.components.BgPage
import com.novamind.app.feature.calendar.components.ColorBorder
import com.novamind.app.feature.calendar.components.ColorCardBg
import com.novamind.app.feature.calendar.components.ColorDark
import com.novamind.app.feature.calendar.components.ColorPrimary
import com.novamind.app.feature.calendar.components.ColorTextError
import com.novamind.app.feature.calendar.components.ColorTextInverse
import com.novamind.app.feature.calendar.components.ColorTextTitle
import com.novamind.app.feature.calendar.components.DayCell
import com.novamind.app.feature.calendar.components.EventRow
import com.novamind.app.feature.calendar.components.MeetingBg
import com.novamind.app.feature.calendar.components.NavArrow
import com.novamind.app.feature.calendar.components.PillIcon
import com.novamind.app.feature.calendar.components.StatCard
import com.novamind.app.feature.calendar.components.TaskRow
import com.novamind.app.feature.calendar.components.TodoBg
import com.novamind.app.feature.calendar.components.animatePlacement
import com.novamind.app.ui.components.AppPullToRefresh
import com.novamind.app.ui.theme.AppTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val weekLetters = listOf("S", "M", "T", "W", "T", "F", "S")

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
    // 周起始改为周日（Figma：S M T W T F S）
    val weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val dateLabel = selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH))

    // 下拉刷新：与首页一致的自定义平级刷新（非系统 PullToRefreshBox）+ status-loading 图标；
    // 仅已连接时真正触发拉取（未连接/游客态下 Refresh 为 no-op）。
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding(),
    ) {
        // 固定头部：与 Home 一致，不参与下拉位移；刷新指示器从标题下方出现。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Calendar", fontSize = 28.sp, fontWeight = FontWeight.Medium, color = ColorTextTitle)
            PillIcon(R.drawable.ic_add, "Add") { onEvent(CalendarUiEvent.AddTaskClicked) }
        }

        AppPullToRefresh(
            // 进入页面的自动同步只更新内容，不让页面跟随下拉指示器位移；
            // 只有用户实际下拉触发 Refresh 时才显示下拉刷新动画。
            isRefreshing = uiState.isPullRefreshing,
            onRefresh = { onEvent(CalendarUiEvent.Refresh) },
            enabled = uiState.isConnected,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            // 左右滑动只在周条（DayCell 区域）内生效：由周条自身的 HorizontalPager 处理，
            // 内容区不挂全局横滑手势，统计卡/议程等区域滑动不切换日期。
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    // The bottom navigation is drawn over the page. Give the disconnected
                    // state enough scroll range to move its connect button fully above it.
                    .padding(bottom = if (uiState.isConnected) 100.dp else 220.dp),
            ) {
        // 日期卡（Figma 959-60167）：日期 + 箭头 + Today 药丸；分隔线；星期表头；周条
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ColorCardBg)
                .padding(top = 12.dp, bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(dateLabel, fontSize = 20.sp, color = ColorTextTitle)
                    NavArrow(left = true) { onEvent(CalendarUiEvent.PrevDay) }
                    NavArrow(left = false) { onEvent(CalendarUiEvent.NextDay) }
                }
                // Today 药丸（描边）：仅当选中日期非今天时显示，点击回到今天
                if (selectedDate != LocalDate.now()) {
                    Surface(
                        shape = RoundedCornerShape(100),
                        color = BgPage,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorder),
                    ) {
                        Text(
                            text = "Today",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = ColorTextTitle,
                            modifier = Modifier
                                .clip(RoundedCornerShape(100))
                                .clickable { onEvent(CalendarUiEvent.DateSelected(LocalDate.now())) }
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // 分隔线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(1.dp)
                    .background(ColorBorder),
            )

            // 星期表头（静态）：S M T W T F S
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                weekLetters.forEach { letter ->
                    Text(
                        text = letter,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ColorTextTitle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 周条：HorizontalPager 支持左右滑动切周。锚点 = 本周周日。
            val anchorSunday = remember { LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)) }
            val weekPage = WEEK_INITIAL_PAGE + ChronoUnit.WEEKS.between(anchorSunday, weekStart).toInt()
            val weekPagerState = rememberPagerState(initialPage = weekPage) { WEEK_PAGE_COUNT }

            // 滑动翻页停定 → 切换选中日期（保持星期几不变）。周日为第 0 天。
            LaunchedEffect(weekPagerState.settledPage) {
                val newStart = anchorSunday.plusWeeks((weekPagerState.settledPage - WEEK_INITIAL_PAGE).toLong())
                if (newStart != weekStart) {
                    onEvent(
                        CalendarUiEvent.DateSelected(
                            newStart.plusDays((selectedDate.dayOfWeek.value % 7).toLong()),
                        ),
                    )
                }
            }
            // 外部改日期跨周时 pager 动画跟随；滑动中不打断手势。
            LaunchedEffect(weekPage) {
                if (weekPagerState.currentPage != weekPage && !weekPagerState.isScrollInProgress) {
                    weekPagerState.animateScrollToPage(weekPage)
                }
            }

            HorizontalPager(
                state = weekPagerState,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            ) { page ->
                val pageStart = anchorSunday.plusWeeks((page - WEEK_INITIAL_PAGE).toLong())
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    (0..6).forEach { i ->
                        val date = pageStart.plusDays(i.toLong())
                        DayCell(
                            day = date.dayOfMonth,
                            selected = date == selectedDate,
                            modifier = Modifier.weight(1f),
                            onClick = { onEvent(CalendarUiEvent.DateSelected(date)) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 统计卡片
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatCard(
                uiState.eventCount.toString(), "Meetings", MeetingBg, R.drawable.illus_stat_meetings,
                Modifier.weight(1f),
                selected = uiState.filter == AgendaFilter.EVENTS,
                onClick = { onEvent(CalendarUiEvent.SelectAgendaFilter(AgendaFilter.EVENTS)) },
            )
            StatCard(
                uiState.taskCount.toString(), "To-dos", TodoBg, R.drawable.illus_stat_todos,
                Modifier.weight(1f),
                selected = uiState.filter == AgendaFilter.TASKS,
                onClick = { onEvent(CalendarUiEvent.SelectAgendaFilter(AgendaFilter.TASKS)) },
            )
        }

        Spacer(Modifier.height(if (uiState.isConnected) 12.dp else 24.dp))

        // Keep the disconnected state compact so the connect action stays clear of bottom nav.
        // Connection errors are intentionally omitted there; retry remains available via button.
        uiState.errorMessage?.takeIf { uiState.isConnected }?.let { message ->
            Text(
                text = message,
                fontSize = 13.sp,
                color = ColorTextError,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        if (!uiState.isConnected) {
            // 未连接（Figma 959-60308）：插画 + 深色「Connect to google calendar」按钮
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                Image(
                    painter = painterResource(R.drawable.illus_calendar_empty),
                    contentDescription = null,
                    modifier = Modifier.width(181.dp).height(137.dp),
                )
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(ColorDark)
                        .clickable { onEvent(CalendarUiEvent.Connect) },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_nav_calendar), null, tint = ColorTextInverse, modifier = Modifier.size(16.dp))
                        Text("Connect to google calendar", color = ColorTextInverse, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // 议程：活动、任务分两个区块（各带小标题）；按 filter 决定显示哪块。
        // 外层已无全局横滑手势，议程区天然不响应左右滑动；任务行右滑完成不受影响。
        if (uiState.isConnected) {
            if (uiState.showEventsSection) {
                AgendaSection(
                    label = "Meetings",
                    count = uiState.eventCount,
                    empty = uiState.events.isEmpty(),
                    emptyText = "No events",
                    loading = uiState.isLoading,
                ) {
                    uiState.events.forEach { event ->
                        EventRow(event, onClick = { onEvent(CalendarUiEvent.EventClicked(event)) })
                    }
                }
            }
            if (uiState.showTasksSection) {
                AgendaSection(
                    label = "To-dos",
                    count = uiState.taskCount,
                    empty = uiState.tasks.isEmpty(),
                    emptyText = "No tasks",
                    loading = uiState.isLoading,
                ) {
                    uiState.tasks.forEach { task ->
                        key(task.id) {
                            TaskRow(
                                task = task,
                                onToggleComplete = { onEvent(CalendarUiEvent.SetTaskCompleted(it, completed = !it.isCompleted)) },
                                onClick = { onEvent(CalendarUiEvent.TaskClicked(it)) },
                                modifier = Modifier.animatePlacement(),
                            )
                        }
                    }
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
