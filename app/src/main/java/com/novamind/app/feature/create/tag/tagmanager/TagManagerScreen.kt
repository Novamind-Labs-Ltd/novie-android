package com.novamind.app.feature.create.tag.tagmanager

import com.novamind.app.util.ToastUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
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
    // 列表底边到窗口底边的真实距离（底栏让位 + 系统栏等，实测得出，避免写死）：由 onGloballyPositioned 填。
    val windowInfo = LocalWindowInfo.current
    var listBottomInsetPx by remember { mutableIntStateOf(0) }
    // 行间距：LazyColumn 行距与「键盘上方留白」共用同一值，避免散落魔法数
    val rowSpacing = 10.dp
    val rowSpacingPx = with(density) { rowSpacing.roundToPx() }

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
                .padding(horizontal = 16.dp)
                // 实测列表底边到窗口底的距离（键盘对位据此换算，避免写死底栏/系统栏让位高度）
                .onGloballyPositioned {
                    listBottomInsetPx =
                        (windowInfo.containerSize.height - it.boundsInWindow().bottom.toInt()).coerceAtLeast(0)
                },
            verticalArrangement = Arrangement.spacedBy(rowSpacing),
            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp + imeBottomDp),
        ) {
            items(ordered, key = { it.id }) { tag ->
                ReorderableItem(reorderState, key = tag.id) { _ ->
                    if (tag.name == renameTarget) {
                        TagEditRow(
                            initialName = tag.name,
                            onConfirm = { newName ->
                                if (existingNames.any { it != tag.name && it.equals(newName, ignoreCase = true) }) {
                                    ToastUtils.short(context, "Tag \"$newName\" already exists")
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

    // 进入行内重命名且键盘弹出后，把编辑项**底边贴到键盘上沿**，并全程跟随键盘平滑上移。
    // 以 imeBottomPx 为 key：键盘高度从 0 动画到最终值，每变一帧重算一次。
    // 关键（防抖动）：每帧只做**一次瞬时** scrollBy 把行对到目标位，绝不用 scrollToItem 硬跳、也不用
    // animateScrollBy（上一帧的补间会被下一帧取消再重启，来回抽搐）。逐帧瞬时对位 = 跟着键盘平滑滑上来。
    // 键盘上沿在列表本地坐标 = viewport 高 − imeBottomPx + 列表底边到窗口底的距离([listBottomInsetPx]，
    // 实测非写死）。行底边再上抬一个行距([rowSpacingPx]) 留白，不紧贴键盘。行只上移不下压（delta>0）。
    LaunchedEffect(renameTarget, imeBottomPx) {
        if (imeBottomPx <= 0) return@LaunchedEffect
        renameTarget?.let { name ->
            val idx = uiState.tags.indexOfFirst { it.name == name }
            if (idx < 0) return@let
            // 该行若已滚出可视区才无动画定位（只在必要时跳，避免每帧硬跳）；正常刚点开时它是可见的
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == idx }) {
                listState.scrollToItem(idx)
            }
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == idx } ?: return@let
            val keyboardTopLocal = info.viewportSize.height - imeBottomPx + listBottomInsetPx
            val desiredTop = keyboardTopLocal - item.size - rowSpacingPx
            val delta = item.offset - desiredTop   // >0 表示行在目标下方（被键盘盖住），需上移
            if (delta > 0) listState.scrollBy(delta.toFloat())
        }
    }

    // 新建标签底部弹窗（交互对齐创建文件夹）
    if (showCreateSheet) {
        CreateTagSheet(
            onCreate = { name, colorHex ->
                // 重名校验（忽略大小写）：已存在则提示且不创建、不关闭弹窗
                if (existingNames.any { it.equals(name, ignoreCase = true) }) {
                    ToastUtils.short(context, "Tag \"$name\" already exists")
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
// 覆盖多色系 + 长短名称，以检验色板和换行渲染。
private val sampleTags = listOf(
    TagRowItem("1", "Brand Identity", "#1B6B45"), // forrest600
    TagRowItem("2", "Competitive Intelligence", "#567828"), // green600
    TagRowItem("3", "Finance", "#FF8C00"), // orange600
    TagRowItem("4", "Human Resources", "#C8391A"), // red500
    TagRowItem("5", "Quality Assurance", "#4A8292"), // teal600
    TagRowItem("6", "Roadmap & Planning", "#708090"), // slate600
    TagRowItem("7", "Miscellaneous", "#656565"), // neutral700
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
