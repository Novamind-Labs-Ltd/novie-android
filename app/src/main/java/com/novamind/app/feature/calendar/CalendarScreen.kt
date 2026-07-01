package com.novamind.app.feature.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.common.config.AppConfig
import com.novamind.app.data.calendar.CalendarEvent
import com.novamind.app.data.calendar.CalendarEventType
import com.novamind.app.data.calendar.isPast
import com.novamind.app.data.tasks.CalendarTask
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.Palette
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// 配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析（不使用硬编码颜色）
private val BgPage: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
private val ColorSurface: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val ColorTextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val ColorTextSub: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
private val ColorTextFaint: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.tertiary.current()
private val ColorTextInverse: Color
    @Composable @ReadOnlyComposable get() = TextColors.Inverse.default.current()
private val ColorTextError: Color
    @Composable @ReadOnlyComposable get() = TextColors.Error.default.current()
private val ColorBorder: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.default.current()
private val ColorPrimary: Color
    @Composable @ReadOnlyComposable get() = IconColors.Brand.default.current()
private val ColorPrimaryBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Primary.default.current()
private val ColorOnPrimary: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.onColor.current()
private val MeetingBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Scenario.teal.current()
private val MeetingIcon: Color
    @Composable @ReadOnlyComposable get() = IconColors.BrandSecondary.default.current()
private val TodoBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.inset.current()
private val TodoIcon: Color
    @Composable @ReadOnlyComposable get() = IconColors.Success.default.current()

private val weekLetters = listOf("M", "T", "W", "T", "F", "S", "S")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

/**
 * 无状态 Calendar 屏幕：仅消费 [CalendarUiState] 并通过 [onEvent] 上报交互。
 * 授权后展示统计与时段事件，未授权时展示「连接 Google 日历」空状态卡片。
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
    val week = (0..6).map { monday.plusDays(it.toLong()) }
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
            Surface(shape = RoundedCornerShape(50), color = ColorSurface, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillIcon(R.drawable.ic_add, "Add")
                    PillIcon(R.drawable.ic_search, "Search")
                    PillIcon(R.drawable.ic_more, "More") { onEvent(CalendarUiEvent.Refresh) }
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

        // 周条
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
            week.forEachIndexed { i, date ->
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

        // 议程：活动、任务分两个区块（各带小标题）；按 filter 决定显示哪块（游客拦截态不展示）
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
                        TaskRow(task, onComplete = { onEvent(CalendarUiEvent.CompleteTask(it)) })
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun PillIcon(iconRes: Int, desc: String, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), desc, tint = ColorTextTitle, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun NavArrow(left: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = if (left) "Previous day" else "Next day",
            tint = ColorTextTitle,
            modifier = Modifier
                .size(22.dp)
                .then(if (left) Modifier.rotate(180f) else Modifier),
        )
    }
}

@Composable
private fun DayCell(
    letter: String,
    day: Int,
    selected: Boolean,
    weekend: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(letter, fontSize = 12.sp, color = if (weekend) ColorTextFaint else ColorTextSub)
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .then(if (selected) Modifier.background(ColorPrimary) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.toString(),
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) ColorTextInverse else ColorTextTitle,
            )
        }
    }
}

@Composable
private fun StatCard(
    count: String,
    label: String,
    bg: Color,
    iconRes: Int,
    iconTint: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .then(if (selected) Modifier.border(2.dp, ColorPrimary, RoundedCornerShape(18.dp)) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .height(92.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconTint.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(48.dp),
        )
        Column(modifier = Modifier.padding(16.dp)) {
            Text(count, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
            Text(label, fontSize = 13.sp, color = ColorTextSub)
        }
    }
}

@Composable
private fun AgendaSection(
    label: String,
    count: Int,
    empty: Boolean,
    emptyText: String,
    loading: Boolean,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 8.dp),
    ) {
        Text(
            "$label ($count)",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = ColorTextSub,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        if (empty) {
            if (!loading) {
                Text(
                    emptyText,
                    fontSize = 13.sp,
                    color = ColorTextFaint,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

/**
 * 任务行：未完成任务支持**右滑完成**（StartToEnd）。
 * 滑动只触发 [onComplete] 后回弹，不真正移除条目——完成态由状态更新驱动（置灰 + 删除线）。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TaskRow(task: CalendarTask, onComplete: (CalendarTask) -> Unit) {
    val currentTask by rememberUpdatedState(task)
    val currentOnComplete by rememberUpdatedState(onComplete)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd && !currentTask.isCompleted) {
                currentOnComplete(currentTask)
            }
            false // 永不真正 dismiss：回弹，由 UiState 变化切换为已完成样式
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !task.isCompleted,
        enableDismissFromEndToStart = false,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)),
        backgroundContent = {
            // 右滑露出的背景：绿色 + 勾选图标，示意「完成」
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(TodoIcon.copy(alpha = 0.18f))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = "Complete task",
                    tint = TodoIcon,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
    ) {
        TaskRowContent(task)
    }
}

@Composable
private fun TaskRowContent(task: CalendarTask) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ColorSurface)
            .border(1.dp, ColorBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 任务用勾选圈图标与活动区分；已完成置灰 + 删除线。
        Icon(
            painter = painterResource(R.drawable.ic_check_circle),
            contentDescription = null,
            tint = if (task.isCompleted) TodoIcon else ColorTextFaint,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = task.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (task.isCompleted) ColorTextFaint else ColorTextTitle,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
            )
            task.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 12.sp, color = ColorTextSub, maxLines = 1)
            }
        }
        Text("Task", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TodoIcon)
    }
}

@Composable
private fun EventRow(event: CalendarEvent) {
    // 已结束的事件视为「已完成」：圆点、标题置灰 + 删除线，与已完成任务保持一致。
    val past = event.isPast
    val accent = if (past) ColorTextFaint else MeetingIcon
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ColorSurface)
            .border(1.dp, ColorBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        Column(Modifier.weight(1f)) {
            Text(
                text = event.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (past) ColorTextFaint else ColorTextTitle,
                textDecoration = if (past) TextDecoration.LineThrough else null,
            )
            event.location?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 12.sp, color = ColorTextSub)
            }
        }
        Text(
            text = if (event.isAllDay) "All day" else event.start.format(timeFormatter),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = ColorTextSub,
        )
    }
}

/** 空状态插图：叠放的笔记本/文件夹 + 装饰圆点（纯 Canvas，无图片资源）。 */
@Composable
private fun CalendarIllustration() {
    // Canvas DrawScope 非 @Composable，颜色令牌须在此先解析为 Color 再传入。
    val primary = ColorPrimary
    val accent = MeetingIcon
    val shadow = Palette.black0
    val cover = Palette.sand550
    val page = Palette.white
    val dotLarge = Palette.sand700
    val dotSmall = Palette.sand550
    Canvas(modifier = Modifier.size(width = 168.dp, height = 124.dp)) {
        val w = size.width
        val h = size.height
        drawOval(shadow, topLeft = Offset(w * 0.20f, h * 0.84f), size = Size(w * 0.60f, h * 0.12f))
        // 后封面
        drawRoundRect(cover, topLeft = Offset(w * 0.26f, h * 0.18f), size = Size(w * 0.46f, h * 0.56f), cornerRadius = CornerRadius(10f, 10f))
        // 白页
        drawRoundRect(page, topLeft = Offset(w * 0.31f, h * 0.24f), size = Size(w * 0.40f, h * 0.52f), cornerRadius = CornerRadius(8f, 8f))
        // 绿色书签/卡
        drawRoundRect(primary, topLeft = Offset(w * 0.30f, h * 0.46f), size = Size(w * 0.14f, h * 0.14f), cornerRadius = CornerRadius(4f, 4f))
        drawRoundRect(accent, topLeft = Offset(w * 0.50f, h * 0.58f), size = Size(w * 0.12f, h * 0.12f), cornerRadius = CornerRadius(4f, 4f))
        // 装饰
        drawCircle(dotLarge, radius = w * 0.05f, center = Offset(w * 0.80f, h * 0.30f))
        drawCircle(dotSmall, radius = w * 0.055f, center = Offset(w * 0.18f, h * 0.66f))
        drawCircle(primary.copy(alpha = 0.4f), radius = w * 0.016f, center = Offset(w * 0.74f, h * 0.7f))
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Calendar · 未连接")
@Composable
private fun CalendarScreenDisconnectedPreview() {
    AppTheme {
        CalendarScreen(
            uiState = CalendarUiState(connectionStatus = CalendarConnectionStatus.NOT_CONNECTED),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Calendar · 游客拦截")
@Composable
private fun CalendarScreenLoginRequiredPreview() {
    AppTheme {
        CalendarScreen(
            uiState = CalendarUiState(connectionStatus = CalendarConnectionStatus.LOGIN_REQUIRED),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Calendar · 已连接")
@Composable
private fun CalendarScreenConnectedPreview() {
    val day = LocalDate.now()
    val sample = listOf(
        // 已结束事件（昨天）：预览中应显示置灰 + 删除线。
        CalendarEvent("0", "Morning sync", false, day.minusDays(1).atTime(9, 30), day.minusDays(1).atTime(10, 0), "Meet", CalendarEventType.DEFAULT),
        CalendarEvent("1", "Team standup", false, day.atTime(9, 30), day.atTime(10, 0), "Meet", CalendarEventType.DEFAULT),
        CalendarEvent("2", "Focus: write spec", false, day.atTime(11, 0), day.atTime(12, 0), null, CalendarEventType.FOCUS_TIME),
        CalendarEvent("3", "Design review", false, day.atTime(14, 0), day.atTime(15, 0), "Room A", CalendarEventType.DEFAULT),
    )
    val sampleTasks = listOf(
        CalendarTask("t1", "list1", "Submit expense report", day, isCompleted = false, notes = null),
        CalendarTask("t2", "list1", "Reply to Alice", day, isCompleted = true, notes = null),
    )
    AppTheme {
        CalendarScreen(
            uiState = CalendarUiState(
                connectionStatus = CalendarConnectionStatus.CONNECTED,
                events = sample,
                tasks = sampleTasks,
            ),
            onEvent = {},
        )
    }
}
