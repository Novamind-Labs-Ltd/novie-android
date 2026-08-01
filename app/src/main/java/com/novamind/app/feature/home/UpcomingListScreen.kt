package com.novamind.app.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.home.components.BgPage
import com.novamind.app.feature.home.components.ColorOnDark
import com.novamind.app.feature.home.components.ColorTextSub
import com.novamind.app.feature.home.components.ColorTextTitle
import com.novamind.app.feature.home.components.UpcomingCard
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.components.AppPullToRefresh
import com.novamind.app.ui.components.TopBarBackButton
import com.novamind.app.ui.theme.AppTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val UpcomingActionColor: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Primary.backgroundSecondary.current()

private data class UpcomingDisplayItem(
    val item: UpcomingItem,
    val dateLabel: String?,
    val timeLabel: String,
    val showAction: Boolean,
)

/**
 * Upcoming 全屏列表页，对齐 Figma home_final 的 Up next 页面：固定顶部标题，
 * 按 This week / Next week 分组展示会议卡，并保留底部渐隐和 120dp 滚动安全区。
 */
@Composable
fun UpcomingListScreen(
    items: List<UpcomingItem>,
    onBack: () -> Unit,
    onItemClick: (UpcomingItem) -> Unit = {},
    onMeetingNotesClick: (UpcomingItem) -> Unit = {},
    isLoading: Boolean = false,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    calendarNeedsAuth: Boolean = false,
    calendarConnecting: Boolean = false,
    onConnectCalendar: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val (thisWeek, nextWeek) = rememberUpcomingGroups(items)
    val hasItems = thisWeek.isNotEmpty() || nextWeek.isNotEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            UpcomingHeader(onBack = onBack)

            AppPullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp),
                ) {
                    if (hasItems) {
                        if (thisWeek.isNotEmpty()) {
                            item(key = "this-week-header") {
                                UpcomingSectionHeader("This week")
                            }
                            item(key = "this-week-content") {
                                UpcomingCardGroup(
                                    items = thisWeek,
                                    onItemClick = onItemClick,
                                    onMeetingNotesClick = onMeetingNotesClick,
                                )
                            }
                        }
                        if (nextWeek.isNotEmpty()) {
                            item(key = "next-week-header") {
                                UpcomingSectionHeader("Next week")
                            }
                            item(key = "next-week-content") {
                                UpcomingCardGroup(
                                    items = nextWeek,
                                    onItemClick = onItemClick,
                                    onMeetingNotesClick = onMeetingNotesClick,
                                )
                            }
                        }
                    } else {
                        item(key = "upcoming-status") {
                            UpcomingStatus(
                                isLoading = isLoading || calendarConnecting,
                                needsAuth = calendarNeedsAuth,
                                onConnectCalendar = onConnectCalendar,
                            )
                        }
                    }
                }
            }
        }

        // Figma mask_nav：列表底部 72dp 渐隐到页面底色，避免最后一张卡硬切到系统手势区。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(72.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            BgPage,
                            BgPage,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun UpcomingStatus(
    isLoading: Boolean,
    needsAuth: Boolean,
    onConnectCalendar: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = ColorTextTitle,
                strokeWidth = 2.dp,
            )
            needsAuth -> {
                Text(
                    text = "Connect Google Calendar to see your upcoming meetings.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = ColorTextSub,
                )
                Surface(
                    onClick = onConnectCalendar,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(100.dp),
                    color = UpcomingActionColor,
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Connect Google Calendar",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = ColorOnDark,
                        )
                    }
                }
            }
            else -> Text(
                text = "No upcoming meetings in the next two weeks.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = ColorTextSub,
            )
        }
    }
}

@Composable
private fun rememberUpcomingGroups(items: List<UpcomingItem>): Pair<List<UpcomingDisplayItem>, List<UpcomingDisplayItem>> {
    val today = LocalDate.now()
    val nextWeekStart = today.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY))
    val hasDates = items.any { it.date != null }
    val source = if (hasDates) {
        items
            .filter { item ->
                val date = item.date ?: return@filter false
                !date.isBefore(today) && date.isBefore(today.plusDays(14))
            }
            .sortedWith(compareBy<UpcomingItem> { it.date }.thenBy { it.time })
    } else {
        items
    }

    val thisWeekSource = if (hasDates) {
        source.filter { it.date?.isBefore(nextWeekStart) == true }
    } else {
        source.take(3)
    }
    val nextWeekSource = if (hasDates) {
        source.filter { it.date?.isBefore(nextWeekStart) == false }
    } else {
        source.drop(3)
    }

    val firstItemId = source.firstOrNull()?.id

    fun display(item: UpcomingItem, index: Int, isNextWeek: Boolean): UpcomingDisplayItem {
        val dateLabel = item.date?.let { date ->
            if (date == today) null else DATE_FORMATTER.format(date).uppercase(Locale.US)
        } ?: when {
            isNextWeek -> "27 JUL"
            index >= 2 -> "27 JUL"
            else -> null
        }
        val timeLabel = when {
            item.isAllDay -> "All day"
            item.time.isNotBlank() -> item.time
            !hasDates && !isNextWeek && index == 1 -> "15:00"
            else -> "10:00"
        }
        return UpcomingDisplayItem(
            item = item,
            dateLabel = dateLabel,
            timeLabel = timeLabel,
            showAction = item.id == firstItemId && item.isMeeting,
        )
    }

    return thisWeekSource.mapIndexed { index, item -> display(item, index, false) } to
            nextWeekSource.mapIndexed { index, item -> display(item, index, true) }
}

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)

@Composable
private fun UpcomingHeader(
    onBack: () -> Unit,
) {
    // Figma top_info：顶部留白 64dp，返回按钮与标题同排 36dp，底部留白 8dp。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp),
    ) {
        Spacer(Modifier.height(64.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopBarBackButton(onClick = onBack)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Up next",
                modifier = Modifier.padding(horizontal = 4.dp),
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                color = ColorTextTitle,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun UpcomingSectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            color = ColorTextTitle,
        )
    }
}

@Composable
private fun UpcomingCardGroup(
    items: List<UpcomingDisplayItem>,
    onItemClick: (UpcomingItem) -> Unit = {},
    onMeetingNotesClick: (UpcomingItem) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgPage)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items.forEach { displayItem ->
            UpcomingCard(
                item = displayItem.item.copy(time = displayItem.timeLabel),
                dateLabel = displayItem.dateLabel,
                showAction = displayItem.showAction,
                actionLabel = if (displayItem.item.noteId == null) "Start notes" else "View notes",
                onAction = { onMeetingNotesClick(displayItem.item) },
                onClick = { onItemClick(displayItem.item) },
            )
        }
    }
}

/** 示例 Upcoming 数据（用于列表页填充）。 */
val sampleUpcoming: List<UpcomingItem> = listOf(
    UpcomingItem("u1", "Monthly report sharing", "Team project progress tracking", R.drawable.ic_upcoming_report, time = "10:00", isMeeting = true),
    UpcomingItem("u2", "Monthly report sharing", "Team project progress tracking", R.drawable.ic_upcoming_meeting, time = "15:00", isMeeting = true),
    UpcomingItem("u3", "Monthly report sharing", "Team project progress tracking", R.drawable.ic_upcoming_report, time = "10:00", isMeeting = true),
    UpcomingItem("u4", "Monthly report sharing", "Team project progress tracking", R.drawable.ic_upcoming_meeting, time = "10:00", isMeeting = true),
    UpcomingItem("u5", "Monthly report sharing", "Team project progress tracking", R.drawable.ic_upcoming_report, time = "10:00", isMeeting = true),
    UpcomingItem("u6", "Monthly report sharing", "Team project progress tracking", R.drawable.ic_upcoming_meeting, time = "10:00", isMeeting = true),
)

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true, name = "Home · Upcoming List Screen")
@Composable
private fun UpcomingListScreenPreview() {
    AppTheme {
        UpcomingListScreen(items = sampleUpcoming, onBack = {})
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Home · Upcoming List Screen (Empty)")
@Composable
private fun UpcomingListScreenEmptyPreview() {
    AppTheme {
        UpcomingListScreen(items = emptyList(), onBack = {})
    }
}
