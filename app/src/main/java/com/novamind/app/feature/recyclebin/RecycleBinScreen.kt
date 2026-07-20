package com.novamind.app.feature.recyclebin

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.R
import com.novamind.app.feature.create.CreateRoute
import com.novamind.app.feature.create.model.NoteItem
import com.novamind.app.feature.recyclebin.components.RecycleBinNoteCard
import com.novamind.app.feature.recyclebin.components.TopIconButton
import com.novamind.app.ui.components.BackButton
import com.novamind.app.ui.components.AppAlertDialog
import com.novamind.app.ui.components.LoadingOverlay
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
    // 每次进入回收站都重拉一次（trashed=true），与首页/Library 一致
    LaunchedEffect(Unit) { viewModel.reload() }
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
                onEmptyAll = viewModel::emptyAll,
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
    onEmptyAll: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showEmptyConfirm by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundColors.Page.default.current())
                .statusBarsPadding()
                .padding(bottom = 24.dp),
        ) {
        // 顶部工具条：返回 / 更多（清空回收站）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(
                onClick = onBack,
                tint = TextColors.Primary.default.current(),
            )
            // 仅当回收站非空时显示「更多」→ 清空回收站
            if (uiState.notes.isNotEmpty()) {
                TopIconButton(
                    iconRes = R.drawable.ic_more,
                    desc = "Empty recycle bin",
                    shape = CircleShape,
                    onClick = { showEmptyConfirm = true },
                )
            }
        }

        // 标题 + 副标题
        Text(
            text = "Recycle Bin",
            fontSize = 24.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
            color = TextColors.Primary.default.current(),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
        )
        Text(
            text = "Shows the days left until they're deleted forever.",
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = TextColors.Primary.secondary.current(),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
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
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalItemSpacing = 14.dp,
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            ) {
                items(uiState.notes, key = { it.id }) { note ->
                    RecycleBinNoteCard(note = note, onClick = { onOpenNote(note.id) })
                }
            }
        }
        }
        // 清空过程中的全局 loading 遮罩；删除+刷新完成后消失
        LoadingOverlay(visible = uiState.isEmptying)
    }

    // 清空回收站二次确认（Figma 879-27320：居中弹窗 + Cancel / 红色 Delete）
    if (showEmptyConfirm) {
        AppAlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = "Empty Recycle Bin",
            message = "Are you sure you want to permanently delete these ${uiState.notes.size} notes? This action cannot be undone.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            destructive = true,
            onConfirm = {
                showEmptyConfirm = false
                onEmptyAll()
            },
        )
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

private val sampleDeleted = listOf(
    NoteItem(
        id = "1",
        title = "",
        preview = "Discussed Q3 KPIs. John to finalize the report by Thursday.",
        updatedAt = System.currentTimeMillis(),
    ),
    NoteItem(
        id = "2",
        title = "Q3 KPIs",
        preview = "Discussed Q3 KPIs. John to finalize the report by Thursday.",
        imagePath = "preview/sample.jpg",
        updatedAt = System.currentTimeMillis() - 3L * 86_400_000L,
    ),
    NoteItem(
        id = "3",
        title = "Q3 KPIs",
        preview = "",
        updatedAt = System.currentTimeMillis() - 20L * 86_400_000L,
    ),
    NoteItem(
        id = "4",
        title = "Pic notes",
        preview = "",
        imagePath = "preview/sample.jpg",
        updatedAt = System.currentTimeMillis() - 26L * 86_400_000L,
    ),
    NoteItem(
        id = "5",
        title = "Q3 KPIs",
        preview = "Discussed Q3 KPIs. John to finalize the report by Thursday.",
        updatedAt = System.currentTimeMillis() - 29L * 86_400_000L,
    ),
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun RecycleBinScreenPreview() {
    AppTheme {
        RecycleBinScreen(uiState = RecycleBinUiState(notes = sampleDeleted))
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Empty Recycle Bin")
@Composable
private fun RecycleBinEmptyPreview() {
    AppTheme {
        RecycleBinScreen(uiState = RecycleBinUiState(notes = emptyList()))
    }
}
