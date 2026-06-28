package com.novamind.app.feature.recyclebin

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.R
import com.novamind.app.feature.create.CreateRoute
import com.novamind.app.feature.note.NoteItem
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

// ─── Route ────────────────────────────────────────────────────────────────────

/**
 * 回收站宿主：管理「列表 ↔ 只读查看」内部转场。点击某条进入只读笔记页（复用 CreateRoute readOnly），
 * 在只读页可恢复 / 彻底删除（删除后由 Flow 自动从列表移除）。
 */
@Composable
fun RecycleBinRoute(
    onBack: () -> Unit = {},
    onFullscreenChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: RecycleBinViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // 当前查看的已删除笔记 id；null = 显示回收站列表
    var selectedNoteId by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = selectedNoteId != null) { selectedNoteId = null }

    AnimatedContent(
        targetState = selectedNoteId,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally { it } + fadeIn())
                    .togetherWith(slideOutHorizontally { -it / 4 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 4 } + fadeIn())
                    .togetherWith(slideOutHorizontally { it } + fadeOut())
            }
        },
        label = "recyclebin_detail",
    ) { noteId ->
        if (noteId != null) {
            // 只读查看：恢复 / 彻底删除后回到列表
            CreateRoute(
                noteId = noteId,
                readOnly = true,
                onBack = { selectedNoteId = null },
                onRestore = {
                    viewModel.restore(noteId)
                    selectedNoteId = null
                },
                onDeleteForever = {
                    viewModel.deleteForever(noteId)
                    selectedNoteId = null
                },
                onFullscreenChange = onFullscreenChange,
                modifier = modifier,
            )
        } else {
            RecycleBinScreen(
                uiState = uiState,
                onBack = onBack,
                onOpenNote = { selectedNoteId = it },
                modifier = modifier,
            )
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun RecycleBinScreen(
    uiState: RecycleBinUiState,
    onBack: () -> Unit = {},
    onOpenNote: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundColors.Page.default.current())
            .statusBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        // 顶部工具条：左侧栏入口 / 更多
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopIconButton(
                iconRes = R.drawable.ic_panel_left,
                desc = "Back",
                shape = RoundedCornerShape(12.dp),
                onClick = onBack,
            )
            TopIconButton(
                iconRes = R.drawable.ic_more,
                desc = "More",
                shape = CircleShape,
            )
        }

        // 标题 + 副标题
        Text(
            text = "Recycle Bin",
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextColors.Primary.default.current(),
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 4.dp),
        )
        Text(
            text = "Shows the days left until they're deleted forever.",
            fontSize = 15.sp,
            color = TextColors.Primary.secondary.current(),
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        )

        if (uiState.notes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Recycle bin is empty.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextColors.Primary.secondary.current(),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            ) {
                items(uiState.notes, key = { it.id }) { note ->
                    RecycleBinNoteCard(note = note, onClick = { onOpenNote(note.id) })
                }
            }
        }
    }
}

@Composable
private fun TopIconButton(
    iconRes: Int,
    desc: String,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(shape)
            .background(BackgroundColors.Surface.default.current())
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = desc,
            tint = TextColors.Primary.default.current(),
            modifier = Modifier.size(20.dp),
        )
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

private val sampleDeleted = listOf(
    NoteItem(
        id = "1",
        title = "Q3 marketing campaign",
        description = "Meeting Summary\nQ3 Strategy: Reviewed competitor analysis and finalized the budget for the upcoming product launch.",
        updatedAt = System.currentTimeMillis(),
    ),
    NoteItem(
        id = "2",
        title = "Team retro",
        description = "Sprint retrospective notes.",
        updatedAt = System.currentTimeMillis() - 5L * 86_400_000L,
    ),
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun RecycleBinScreenPreview() {
    AppTheme {
        RecycleBinScreen(uiState = RecycleBinUiState(notes = sampleDeleted))
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "空回收站")
@Composable
private fun RecycleBinEmptyPreview() {
    AppTheme {
        RecycleBinScreen(uiState = RecycleBinUiState(notes = emptyList()))
    }
}
