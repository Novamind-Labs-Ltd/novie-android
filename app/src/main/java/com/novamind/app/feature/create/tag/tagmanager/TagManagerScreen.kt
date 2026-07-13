package com.novamind.app.feature.create.tag.tagmanager

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.R
import com.novamind.app.feature.create.tag.tagmanager.components.BgCard
import com.novamind.app.feature.create.tag.tagmanager.components.BgPage
import com.novamind.app.feature.create.tag.tagmanager.components.ChangeTagColorSheet
import com.novamind.app.feature.create.tag.tagmanager.components.ColorTextTitle
import com.novamind.app.feature.create.tag.tagmanager.components.DeleteTagSheet
import com.novamind.app.feature.create.tag.tagmanager.components.SwipeToDeleteRow
import com.novamind.app.feature.create.tag.tagmanager.components.TagEditRow
import com.novamind.app.feature.create.tag.tagmanager.components.TagRow
import com.novamind.app.feature.create.tag.tagmanager.components.TopIconButton
import com.novamind.app.ui.components.BackButton
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.novamind.app.ui.theme.AppTheme

// 配色与视觉组件在 feature/create/tag/tagmanager/components 包（TagManagerColors 等），本文件只保留编排。

// ─── Route ────────────────────────────────────────────────────────────────────

@Composable
fun TagManagerRoute(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TagManagerViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TagManagerScreen(
        uiState = uiState,
        onBack = onBack,
        onCreateTag = viewModel::createTag,
        onRenameTag = viewModel::renameTag,
        onDeleteTag = viewModel::deleteTag,
        onChangeTagColor = viewModel::changeTagColor,
        onReorderTags = viewModel::reorderTags,
        modifier = modifier,
    )
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun TagManagerScreen(
    uiState: TagManagerUiState,
    onBack: () -> Unit = {},
    onCreateTag: (name: String, colorHex: String) -> Unit = { _, _ -> },
    onRenameTag: (old: String, new: String) -> Unit = { _, _ -> },
    onDeleteTag: (String) -> Unit = {},
    onChangeTagColor: (name: String, colorHex: String) -> Unit = { _, _ -> },
    onReorderTags: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // rename 目标标签名（行内编辑）；showCreateSheet = 新建标签底部弹窗；colorTarget = 改色目标
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var colorTarget by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeBottomDp = with(density) { imeBottomPx.toDp() }

    val existingNames = uiState.tags.map { it.name }

    // 拖拽排序：sh.calvin.reorderable（长按整行拖动）。ordered 本地顺序副本：拖动中由 onMove 改写、
    // 非拖拽时从 uiState.tags 同步；抬起（onDragStopped）提交标签名序列给 onReorderTags。
    var ordered by remember { mutableStateOf(uiState.tags) }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        ordered = ordered.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    LaunchedEffect(uiState.tags, reorderState.isAnyItemDragging) {
        if (!reorderState.isAnyItemDragging) ordered = uiState.tags
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        // 顶部工具条：侧栏入口 / 新建
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack, background = BgCard, tint = ColorTextTitle)
            TopIconButton(R.drawable.ic_add, "New tag", CircleShape) {
                renameTarget = null
                showCreateSheet = true
            }
        }

        Text(
            text = "Tag manager",
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ColorTextTitle,
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp + imeBottomDp),
        ) {
            items(ordered, key = { it.id }) { tag ->
                ReorderableItem(reorderState, key = tag.id) { _ ->
                    if (tag.name == renameTarget) {
                        TagEditRow(
                            initialName = tag.name,
                            onConfirm = { newName ->
                                if (existingNames.any { it != tag.name && it.equals(newName, ignoreCase = true) }) {
                                    Toast.makeText(context, "Tag \"$newName\" already exists", Toast.LENGTH_SHORT).show()
                                } else {
                                    onRenameTag(tag.name, newName)
                                    renameTarget = null
                                }
                            },
                            onCancel = { renameTarget = null },
                        )
                    } else {
                        // 左滑删除保留；纵向拖拽换序由库的长按把手（作用于整行）提供
                        SwipeToDeleteRow(
                            modifier = Modifier.longPressDraggableHandle(
                                onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onDragStopped = { onReorderTags(ordered.map { it.name }) },
                            ),
                            onDelete = { deleteTarget = tag.name },
                        ) {
                            TagRow(
                                tag = tag,
                                onRename = { renameTarget = tag.name },
                                onDelete = { deleteTarget = tag.name },
                                onChangeColor = { colorTarget = tag.name },
                            )
                        }
                    }
                }
            }
        }
    }

    // 进入行内重命名且键盘弹出后，把编辑项滚到可视区
    LaunchedEffect(renameTarget, imeBottomPx > 0) {
        if (imeBottomPx <= 0) return@LaunchedEffect
        renameTarget?.let { name ->
            val idx = uiState.tags.indexOfFirst { it.name == name }
            if (idx >= 0) listState.animateScrollToItem(idx)
        }
    }

    // 新建标签底部弹窗（交互对齐创建文件夹）
    if (showCreateSheet) {
        CreateTagSheet(
            onCreate = { name, colorHex ->
                // 重名校验（忽略大小写）：已存在则提示且不创建、不关闭弹窗
                if (existingNames.any { it.equals(name, ignoreCase = true) }) {
                    Toast.makeText(context, "Tag \"$name\" already exists", Toast.LENGTH_SHORT).show()
                } else {
                    onCreateTag(name, colorHex)
                    showCreateSheet = false
                }
            },
            onDismiss = { showCreateSheet = false },
        )
    }

    // 改颜色底部弹层（点击标签图标）
    colorTarget?.let { target ->
        val currentHex = uiState.tags.firstOrNull { it.name == target }?.colorHex
        ChangeTagColorSheet(
            currentHex = currentHex,
            onPick = { hex ->
                onChangeTagColor(target, hex)
                colorTarget = null
            },
            onDismiss = { colorTarget = null },
        )
    }

    // 删除二次确认
    deleteTarget?.let { target ->
        DeleteTagSheet(
            tagName = target,
            onConfirm = {
                onDeleteTag(target)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

// 预览数据：颜色取自 AppConfig.Folder.COLORS（Palette 各色系代表色），
// 覆盖多色系 + 长短名称 + 不同关联笔记数（含 0），以检验色板、换行与计数渲染。
private val sampleTags = listOf(
    TagRowItem("1", "Brand Identity", "#1B6B45", 12), // forrest600
    TagRowItem("2", "Competitive Intelligence", "#567828", 128), // green600
    TagRowItem("3", "Finance", "#FF8C00", 3), // orange600
    TagRowItem("4", "Human Resources", "#C8391A", 0), // red500
    TagRowItem("5", "Quality Assurance", "#4A8292", 47), // teal600
    TagRowItem("6", "Roadmap & Planning", "#708090", 9), // slate600
    TagRowItem("7", "Miscellaneous", "#656565", 1), // neutral700
)

@Preview(showBackground = true, showSystemUi = true, name = "Create · TagManagerScreen")
@Composable
private fun TagManagerScreenPreview() {
    AppTheme {
        TagManagerScreen(uiState = TagManagerUiState(tags = sampleTags))
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Create · TagManagerScreen · Single")
@Composable
private fun TagManagerScreenSinglePreview() {
    AppTheme {
        TagManagerScreen(uiState = TagManagerUiState(tags = sampleTags.take(1)))
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Create · TagManagerScreen · Empty")
@Composable
private fun TagManagerScreenEmptyPreview() {
    AppTheme {
        TagManagerScreen(uiState = TagManagerUiState(tags = emptyList()))
    }
}
