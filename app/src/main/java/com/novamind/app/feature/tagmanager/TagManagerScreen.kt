package com.novamind.app.feature.tagmanager

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.R
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.Palette
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.ColorUtils

// 配色：统一引用 ui/colors 设计系统令牌
private val BgPage: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
private val BgCard: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val ColorTextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val ColorTextSub: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
private val ColorBorder: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.default.current()
private val ColorAccent: Color
    @Composable @ReadOnlyComposable get() = IconColors.Brand.default.current()

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
        modifier = modifier,
    )
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun TagManagerScreen(
    uiState: TagManagerUiState,
    onBack: () -> Unit = {},
    onCreateTag: (String) -> Unit = {},
    onRenameTag: (old: String, new: String) -> Unit = { _, _ -> },
    onDeleteTag: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 行内编辑：rename 目标标签名；creating = 顶部新建行
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeBottomDp = with(density) { imeBottomPx.toDp() }

    val existingNames = uiState.tags.map { it.name }

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
            TopIconButton(R.drawable.ic_panel_left, "Back", RoundedCornerShape(12.dp), onBack)
            TopIconButton(R.drawable.ic_add, "New tag", CircleShape) {
                renameTarget = null
                creating = true
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
            // 顶部新建行
            if (creating) {
                item(key = "__new__") {
                    TagEditRow(
                        initialName = "",
                        onConfirm = { name ->
                            if (existingNames.any { it.equals(name, ignoreCase = true) }) {
                                Toast.makeText(context, "Tag \"$name\" already exists", Toast.LENGTH_SHORT).show()
                            } else {
                                onCreateTag(name)
                                creating = false
                            }
                        },
                        onCancel = { creating = false },
                    )
                }
            }
            itemsIndexed(uiState.tags, key = { _, it -> it.id }) { _, tag ->
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
                    TagRow(
                        tag = tag,
                        onRename = { renameTarget = tag.name; creating = false },
                        onDelete = { deleteTarget = tag.name },
                    )
                }
            }
        }
    }

    // 进入编辑且键盘弹出后，把编辑项滚到可视区
    LaunchedEffect(renameTarget, creating, imeBottomPx > 0) {
        if (imeBottomPx <= 0) return@LaunchedEffect
        if (creating) {
            listState.animateScrollToItem(0)
        } else renameTarget?.let { name ->
            val idx = uiState.tags.indexOfFirst { it.name == name }
            if (idx >= 0) listState.animateScrollToItem(idx)
        }
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

/** 标签行：标签图标 + 名称 + 笔记数 + 更多。 */
@Composable
private fun TagRow(
    tag: TagRowItem,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = ColorUtils.parseHexColor(tag.colorHex) ?: ColorAccent
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ColorBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_tag),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = tag.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = ColorTextTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(tag.noteCount.toString(), fontSize = 14.sp, color = ColorTextSub)
            Box {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { menuExpanded = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more),
                        contentDescription = "More",
                        tint = ColorTextSub,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = BgCard,
                    shadowElevation = 8.dp,
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", fontSize = 16.sp, color = ColorTextTitle) },
                        onClick = { menuExpanded = false; onRename() },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", fontSize = 16.sp, color = IconColors.Error.default.current()) },
                        onClick = { menuExpanded = false; onDelete() },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** 行内编辑（新建/重命名）：× 取消 + 自动聚焦输入框（光标在末尾）+ 绿色 ✓ 确认。 */
@Composable
private fun TagEditRow(
    initialName: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var value by remember(initialName) {
        mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length)))
    }
    val trimmed = value.text.trim()
    val canConfirm = trimmed.isNotEmpty() && trimmed != initialName
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun confirm() {
        if (canConfirm) onConfirm(trimmed)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = "Cancel",
            tint = ColorTextTitle,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onCancel)
                .padding(2.dp),
        )
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            color = BgCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorder),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = ColorTextTitle),
                cursorBrush = SolidColor(ColorTextTitle),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_check_circle),
            contentDescription = "Confirm",
            tint = if (canConfirm) ColorAccent else ColorTextSub,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(enabled = canConfirm) { confirm() },
        )
    }
}

/** 删除标签二次确认（底部弹层）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteTagSheet(
    tagName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BgCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Delete “$tagName” tag?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ColorTextTitle,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "This removes the tag from all notes.",
                fontSize = 14.sp,
                color = ColorTextSub,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
            Box(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(BackgroundColors.Error.default.current())
                    .clickable(onClick = onConfirm)
                    .height(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Delete", color = Palette.white, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, ColorTextTitle, RoundedCornerShape(50))
                    .clickable(onClick = onDismiss)
                    .height(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Cancel", color = ColorTextTitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
            .background(BgCard)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = desc,
            tint = ColorTextTitle,
            modifier = Modifier.size(20.dp),
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

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun TagManagerScreenPreview() {
    AppTheme {
        TagManagerScreen(uiState = TagManagerUiState(tags = sampleTags))
    }
}
