package com.novamind.app.feature.create.tag.tagmanager

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.feature.create.tag.tagmanager.components.BgCard
import com.novamind.app.feature.create.tag.tagmanager.components.BgPage
import com.novamind.app.feature.create.tag.tagmanager.components.ChangeTagColorSheet
import com.novamind.app.feature.create.tag.tagmanager.components.ColorTextTitle
import com.novamind.app.feature.create.tag.tagmanager.components.DeleteTagSheet
import com.novamind.app.feature.create.tag.tagmanager.components.SwipeToDeleteRow
import com.novamind.app.feature.create.tag.tagmanager.components.TagEditRow
import com.novamind.app.feature.create.tag.tagmanager.components.TagRow
import com.novamind.app.feature.create.tag.tagmanager.components.TopIconButton
import com.novamind.app.R
import com.novamind.app.ui.components.BackButton
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

    // 拖拽排序：本地稳定副本（非拖拽时从 uiState 同步），及拖拽状态
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var draggedDistance by remember { mutableFloatStateOf(0f) }
    var initialItemOffset by remember { mutableStateOf(0) }
    var initialItemSize by remember { mutableStateOf(0) }
    val tagItems = remember { mutableStateListOf<TagRowItem>() }
    LaunchedEffect(uiState.tags, draggingIndex) {
        if (draggingIndex == null) {
            tagItems.clear()
            tagItems.addAll(uiState.tags)
        }
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
            itemsIndexed(tagItems, key = { _, it -> it.id }) { index, tag ->
                val isDragging = index == draggingIndex
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
                    // 拖拽项：translationY 跟手；其余项 animateItem 平滑让位
                    val rowModifier = if (isDragging) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer {
                                val current = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == tag.id }?.offset ?: initialItemOffset
                                translationY = initialItemOffset + draggedDistance - current
                            }
                    } else {
                        Modifier.animateItem()
                    }
                    SwipeToDeleteRow(
                        modifier = rowModifier,
                        onDelete = { deleteTarget = tag.name },
                        onReorderStart = {
                            draggingIndex = tagItems.indexOfFirst { it.id == tag.id }
                            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == tag.id }
                            initialItemOffset = info?.offset ?: 0
                            initialItemSize = info?.size ?: 0
                            draggedDistance = 0f
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onReorderDrag = { dy ->
                            val from = draggingIndex
                            if (from != null) {
                                draggedDistance += dy
                                val draggedCenter = initialItemOffset + draggedDistance + initialItemSize / 2f
                                val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                    info.index != from &&
                                        draggedCenter.toInt() in info.offset..(info.offset + info.size)
                                }
                                if (target != null && target.index < tagItems.size) {
                                    tagItems.add(target.index, tagItems.removeAt(from))
                                    draggingIndex = target.index
                                }
                            }
                        },
                        onReorderEnd = {
                            if (draggingIndex != null) onReorderTags(tagItems.map { it.name })
                            draggingIndex = null
                            draggedDistance = 0f
                        },
                        onReorderCancel = { draggingIndex = null; draggedDistance = 0f },
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
            onConfirm = { onDeleteTag(target); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

private val sampleTags = listOf(
    TagRowItem("1", "Brand Identity", "#3D7A5A", 10),
    TagRowItem("2", "Competitive Intelligence", "#3D7A5A", 10),
    TagRowItem("3", "Financial Reporting", "#3D7A5A", 10),
    TagRowItem("4", "Human Resources", "#3D7A5A", 0),
    TagRowItem("5", "Quality Assurance", "#3D7A5A", 10),
)

@Preview(showBackground = true, showSystemUi = true, name = "Create · TagManagerScreen")
@Composable
private fun TagManagerScreenPreview() {
    AppTheme {
        TagManagerScreen(uiState = TagManagerUiState(tags = sampleTags))
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Create · TagManagerScreen · Empty")
@Composable
private fun TagManagerScreenEmptyPreview() {
    AppTheme {
        TagManagerScreen(uiState = TagManagerUiState(tags = emptyList()))
    }
}
