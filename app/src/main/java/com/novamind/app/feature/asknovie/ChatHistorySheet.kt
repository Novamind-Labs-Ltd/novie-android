package com.novamind.app.feature.asknovie

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.asknovie.components.Bg
import com.novamind.app.feature.asknovie.components.HistoryCardBg
import com.novamind.app.feature.asknovie.components.SearchField
import com.novamind.app.feature.asknovie.components.SubColor
import com.novamind.app.feature.asknovie.components.TitleColor
import com.novamind.app.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

private const val HISTORY_PAGE_SIZE = 20

/**
 * Ask Novie 会话历史全屏页（Figma 1321:36303）。
 * 首次展示 20 条；上拉接近列表末尾时每次继续追加 20 条。
 */
@Composable
fun ChatHistoryScreen(
    onBack: () -> Unit,
    onSelectSession: (ChatSession) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf("") }
    var visibleCount by remember(query) { mutableIntStateOf(HISTORY_PAGE_SIZE) }
    val sessions = remember { ChatSessionStore.load(context) }
    val filtered = remember(query, sessions) {
        if (query.isBlank()) {
            sessions
        } else {
            sessions.filter { session ->
                session.title.contains(query, ignoreCase = true) ||
                    session.previewText().contains(query, ignoreCase = true)
            }
        }
    }
    val visibleSessions = remember(filtered, visibleCount) {
        filtered.take(visibleCount)
    }
    val listState = rememberLazyListState()

    BackHandler(onBack = onBack)

    HistoryScreenContent(
        sessions = sessions,
        visibleSessions = visibleSessions,
        query = query,
        onQueryChange = { query = it },
        onBack = onBack,
        onSelectSession = onSelectSession,
        listState = listState,
        hasMore = visibleSessions.size < filtered.size,
        onLoadMore = {
            visibleCount = min(visibleCount + HISTORY_PAGE_SIZE, filtered.size)
        },
    )
}

@Composable
private fun HistoryScreenContent(
    sessions: List<ChatSession>,
    visibleSessions: List<ChatSession>,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onSelectSession: (ChatSession) -> Unit,
    listState: LazyListState,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
) {
    val shouldLoadMore by remember(listState, visibleSessions, hasMore) {
        derivedStateOf {
            if (!hasMore || visibleSessions.isEmpty()) return@derivedStateOf false
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
                    .height(36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "Back",
                    tint = TitleColor,
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false),
                            onClick = onBack,
                        )
                        .padding(6.dp),
                )
                Text(
                    text = "History",
                    color = TitleColor,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = "Search conversations",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            val emptyMessage = when {
                sessions.isEmpty() -> "No conversation history yet"
                query.isNotBlank() -> "No matching conversations"
                else -> null
            }
            if (visibleSessions.isEmpty()) {
                emptyMessage?.let {
                    Text(
                        text = it,
                        color = SubColor,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                    )
                }
            } else {
                val grouped = remember(visibleSessions) { visibleSessions.groupByHistoryPeriod() }
                val headerPositions = remember(grouped) {
                    buildList {
                        var itemIndex = 0
                        grouped.entries.forEachIndexed { groupIndex, (label, sessions) ->
                            if (groupIndex > 0) itemIndex++ // section spacer
                            add(itemIndex to label)
                            itemIndex++ // section header
                            itemIndex += sessions.size
                        }
                    }
                }
                val pinnedLabel by remember(listState, headerPositions) {
                    derivedStateOf {
                        val firstIndex = listState.firstVisibleItemIndex
                        val crossedVisibleHeader = listState.layoutInfo.visibleItemsInfo
                            .lastOrNull { item ->
                                item.offset <= 0 && headerPositions.any { it.first == item.index }
                            }
                            ?.index
                        val activeIndex = crossedVisibleHeader
                            ?: headerPositions.lastOrNull { it.first < firstIndex }?.first
                        headerPositions.firstOrNull { it.first == activeIndex }?.second
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            top = 16.dp,
                            end = 16.dp,
                            bottom = 24.dp,
                        ),
                    ) {
                        grouped.entries.forEachIndexed { groupIndex, (label, groupSessions) ->
                            if (groupIndex > 0) item(key = "space-$label") { Spacer(Modifier.height(24.dp)) }
                            item(key = "header-$label") {
                                HistorySectionHeader(
                                    label = label,
                                    // 吸顶副本出现后隐藏列表内的同名标题，
                                    // 仅保留原高度，避免 item 位置跳动。
                                    modifier = Modifier.alpha(if (pinnedLabel == label) 0f else 1f),
                                )
                            }
                            items(groupSessions, key = { it.id }) { session ->
                                HistorySessionCard(
                                    session = session,
                                    onClick = { onSelectSession(session) },
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                            }
                        }
                    }

                    pinnedLabel?.let { label ->
                        HistorySectionHeader(
                            label = label,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySectionHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Bg,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            color = SubColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun HistorySessionCard(
    session: ChatSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = HistoryCardBg,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 63.dp)
            .animateContentSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = session.title,
                    color = TitleColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = session.historyTimeLabel(),
                    color = SubColor,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            Text(
                text = session.previewText(),
                color = SubColor,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun ChatSession.previewText(): String =
    messages.asReversed().firstNotNullOfOrNull { message ->
        message.text.trim().takeIf { it.isNotEmpty() }
            ?: message.attachments.firstOrNull()?.name
    }.orEmpty()

private fun List<ChatSession>.groupByHistoryPeriod(): Map<String, List<ChatSession>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    return groupBy { session ->
        val date = Instant.ofEpochMilli(session.updatedAt).atZone(zone).toLocalDate()
        when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            !date.isBefore(today.minusDays(7)) -> "Previous 7 days"
            else -> date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
        }
    }
}

private fun ChatSession.historyTimeLabel(): String {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(updatedAt).atZone(zone)
    val today = LocalDate.now(zone)
    return when {
        time.toLocalDate() == today || time.toLocalDate() == today.minusDays(1) ->
            time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
        !time.toLocalDate().isBefore(today.minusDays(7)) ->
            time.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
        else -> time.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }
}

private fun previewSessions(): List<ChatSession> {
    val now = System.currentTimeMillis()
    return listOf(
        ChatSession("1", "First CS hire", now, listOf(ChatMessage(Role.Assistant, "Seniority × specialization for the first CS hire…"))),
        ChatSession("2", "Note-taking habits", now - 3_600_000, listOf(ChatMessage(Role.Assistant, "Capture first, organise later — a daily review habit."))),
        ChatSession("3", "Q3 KPIs follow-up", now - 86_400_000, listOf(ChatMessage(Role.Assistant, "John to finalize the report by Thursday."))),
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 917, name = "Chat History · Figma")
@Composable
private fun ChatHistoryScreenPreview() {
    AppTheme {
        val sessions = previewSessions()
        HistoryScreenContent(
            sessions = sessions,
            visibleSessions = sessions,
            query = "",
            onQueryChange = {},
            onBack = {},
            onSelectSession = {},
            listState = rememberLazyListState(),
            hasMore = false,
            onLoadMore = {},
        )
    }
}
