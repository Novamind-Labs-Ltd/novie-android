package com.novamind.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.theme.AppTheme

// ─── 颜色 ─────────────────────────────────────────────────────────────────────

private val BgPage = Color(0xFFF0EFEA)
private val BgCard = Color(0xFFFFFFFF)
private val BgActionBar = Color(0xFFFFFFFF)
private val ColorPrimary = Color(0xFF3D7A5A)
private val ColorTextTitle = Color(0xFF1A1A1A)
private val ColorTextSub = Color(0xFF6B6B6B)
private val ColorTextHint = Color(0xFFAAAAAA)
private val ColorBorder = Color(0xFFE0E0E0)
private val ColorSelectedBorder = Color(0xFFAAD4C8)

// ─── 无状态 Screen ────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onSearchQueryChange: (String) -> Unit,
    onUpcomingSeeAll: () -> Unit,
    onNotesSeeAll: () -> Unit,
    onNoteClick: (noteId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(bottom = 100.dp), // 为悬浮导航栏留空间
        ) {
            // ── 顶部栏 ──────────────────────────────────────────────────────
            TopBar(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))

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
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    uiState.notes.forEach { note ->
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
private fun TopBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // 头像
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFFD0C8B8)),
            contentAlignment = Alignment.Center,
        ) {
            // 用占位图代替真实头像
            Text(text = "👤", fontSize = 22.sp)
        }

        // 通知 + 更多操作
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
                Icon(
                    painter = painterResource(id = R.drawable.ic_notification),
                    contentDescription = "Notifications",
                    tint = ColorTextTitle,
                    modifier = Modifier.size(22.dp),
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_more),
                    contentDescription = "More",
                    tint = ColorTextTitle,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
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

// ─── Note 卡片 ────────────────────────────────────────────────────────────────

@Composable
private fun NoteCard(
    note: NoteItem,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val borderColor = if (note.isSelected) ColorSelectedBorder else ColorBorder
    val borderWidth = if (note.isSelected) 1.5.dp else 1.dp

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(160.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        shadowElevation = if (note.isSelected) 3.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 文件夹标签
            if (note.folderName != null) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF3D7A5A).copy(alpha = 0.08f),
                ) {
                    Text(
                        text = "📁 ${note.folderName}",
                        fontSize = 10.sp,
                        color = Color(0xFF3D7A5A),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                text = note.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ColorTextTitle,
                maxLines = 2,
            )
            HorizontalDivider(Modifier, thickness = 0.8.dp, color = ColorBorder)
            if (note.description.isNotBlank()) {
                Text(
                    text = note.description,
                    fontSize = 12.sp,
                    color = ColorTextSub,
                    lineHeight = 17.sp,
                    maxLines = 4,
                )
            }
            // Tags
            if (note.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    note.tags.take(2).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF6B6B6B).copy(alpha = 0.08f),
                        ) {
                            Text(
                                text = tag,
                                fontSize = 10.sp,
                                color = Color(0xFF6B6B6B),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
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
                    NoteItem("1", "Market research", "Here is an overview of your competitors in 2026.\n\n3 new competitor in the market, they all boutique studios in..."),
                    NoteItem("2", "Market research", "Here is an overview of your competitors in 2026.\n\n3 new competitor in the market, they all boutique studios in...", isSelected = true),
                    NoteItem("3", "Market research", "Here is an overview of your competitors in 2026.\n\n3 new competitor in the market, they all boutique studios in..."),
                ),
            ),
            onSearchQueryChange = {},
            onUpcomingSeeAll = {},
            onNotesSeeAll = {},
        )
    }
}
