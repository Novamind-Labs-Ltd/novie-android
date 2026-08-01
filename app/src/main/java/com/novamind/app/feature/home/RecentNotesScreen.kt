package com.novamind.app.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.feature.create.model.NoteItem
import com.novamind.app.feature.home.components.BgPage
import com.novamind.app.feature.home.components.ColorTextSub
import com.novamind.app.feature.home.components.ColorTextTitle
import com.novamind.app.feature.home.components.RecentNoteCard
import com.novamind.app.feature.library.LibraryViewModel
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.components.AppPullToRefresh
import com.novamind.app.ui.components.TopBarBackButton
import com.novamind.app.ui.theme.AppTheme

@Composable
fun RecentNotesRoute(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.reloadNotes() }

    RecentNotesScreen(
        notes = uiState.notes,
        isInitialLoading = uiState.isInitialLoading,
        isRefreshing = uiState.isRefreshingNotes,
        isLoadingMore = uiState.isLoadingMore,
        hasMore = uiState.hasMoreNotes,
        onBack = onBack,
        onOpenNote = onOpenNote,
        onRefresh = viewModel::refreshNotes,
        onLoadMore = viewModel::loadMoreNotes,
        modifier = modifier,
    )
}

/** Figma 1499:54132：Home Recent notes 的全屏二级列表。 */
@Composable
fun RecentNotesScreen(
    notes: List<NoteItem>,
    isInitialLoading: Boolean,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val listState = rememberLazyListState()
    val reachedEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(reachedEnd, hasMore, isLoadingMore, isRefreshing) {
        if (reachedEnd && hasMore && !isLoadingMore && !isRefreshing) onLoadMore()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding(),
    ) {
        RecentNotesHeader(onBack = onBack)
        AppPullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            when {
                isInitialLoading && notes.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = IconColors.Success.default.current())
                }
                notes.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "No recent notes", color = ColorTextSub, fontSize = 16.sp)
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(notes, key = { it.id }) { note ->
                        RecentNoteCard(note = note, onClick = { onOpenNote(note.id) })
                    }
                    if (isLoadingMore) {
                        item(key = "load-more") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = IconColors.Success.default.current(),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentNotesHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopBarBackButton(onClick = onBack)
        Text(
            text = "Recent notes",
            modifier = Modifier.padding(start = 8.dp),
            color = ColorTextTitle,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Home · Recent notes")
@Composable
private fun RecentNotesScreenPreview() {
    AppTheme {
        RecentNotesScreen(
            notes = listOf(
                NoteItem("1", "", "Discussed Q3 KPIs. John to finalize the report by Thursday."),
                NoteItem("2", "Q3 KPIs", "Discussed Q3 KPIs. John to finalize the report by Thursday."),
                NoteItem("3", "Q3 KPIs", ""),
            ),
            isInitialLoading = false,
            isRefreshing = false,
            isLoadingMore = false,
            hasMore = false,
            onBack = {},
            onOpenNote = {},
            onRefresh = {},
            onLoadMore = {},
        )
    }
}
