package com.novamind.app.feature.create

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.R
import com.novamind.app.feature.create.components.FolderPickerSheet
import com.novamind.app.feature.create.components.TagChip
import com.novamind.app.feature.create.components.TagPickerSheet
import com.novamind.app.ui.theme.AppTheme
import java.util.Date

private val BgPage = Color(0xFFF0EFEA)
private val ColorTextTitle = Color(0xFF1A1A1A)
private val ColorTextHint = Color(0xFFAAAAAA)
private val ColorTextSub = Color(0xFF6B6B6B)
private val ColorChipBg = Color(0xFFFFFFFF)

// ─── Route ────────────────────────────────────────────────────────────────────

@Composable
fun CreateRoute(
    onBack: () -> Unit = {},
    noteId: String? = null,           // 非 null 时加载已有笔记
    modifier: Modifier = Modifier,
    viewModel: CreateViewModel = viewModel(),
) {
    // 进入页面时：有 noteId 则加载已有笔记，否则新建
    LaunchedEffect(noteId) {
        if (noteId != null) viewModel.loadNote(noteId)
        else viewModel.reset()
    }
    // 收一次性导航事件
    LaunchedEffect(Unit) {
        viewModel.navigateBack.collect { onBack() }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CreateScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun CreateScreen(
    uiState: CreateUiState,
    onEvent: (CreateEvent) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val imeVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    val timeLabel = remember { DateFormat.format("Today HH:mm", Date()).toString() }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgPage)
                .statusBarsPadding()
                .imePadding(),
        ) {
            // ── 顶部操作行：返回 + 保存 ───────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = ColorChipBg, shadowElevation = 2.dp) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false),
                                onClick = onBack,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                            tint = ColorTextTitle,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // 保存按钮
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF3D7A5A),
                    shadowElevation = 2.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = { onEvent(CreateEvent.SaveNote) },
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Save",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }

            // ── 标题 ──────────────────────────────────────────────────────
            BasicTextField(
                value = uiState.title,
                onValueChange = { onEvent(CreateEvent.TitleChanged(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                textStyle = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ColorTextTitle,
                ),
                cursorBrush = SolidColor(ColorTextTitle),
                decorationBox = { inner ->
                    if (uiState.title.isEmpty()) {
                        Text("New note", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = ColorTextTitle)
                    }
                    inner()
                },
            )

            // ── Meta 操作行 ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 文件夹 chip
                MetaChip(
                    iconResId = R.drawable.ic_nav_library,
                    label = uiState.selectedFolder?.let { "${it.iconEmoji} ${it.name}" } ?: "Add to folder",
                    isActive = uiState.selectedFolder != null,
                    onClick = { onEvent(CreateEvent.ShowFolderPicker) },
                )
                // 标签 chip（已选标签 + 添加入口）
                if (uiState.selectedTags.isEmpty()) {
                    MetaChip(
                        iconResId = R.drawable.ic_nav_brand,
                        label = "Tags",
                        onClick = { onEvent(CreateEvent.ShowTagPicker) },
                    )
                } else {
                    uiState.selectedTags.forEach { tag ->
                        TagChip(
                            tag = tag,
                            isSelected = true,
                            onClick = { onEvent(CreateEvent.ShowTagPicker) },
                        )
                    }
                    // + 添加更多标签
                    MetaChip(
                        iconResId = R.drawable.ic_nav_brand,
                        label = "+",
                        onClick = { onEvent(CreateEvent.ShowTagPicker) },
                    )
                }
                // 时间 chip
                MetaChip(
                    iconResId = R.drawable.ic_nav_calendar,
                    label = timeLabel,
                )
            }

            // ── 正文 ──────────────────────────────────────────────────────
            BasicTextField(
                value = uiState.body,
                onValueChange = { onEvent(CreateEvent.BodyChanged(it)) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    color = ColorTextTitle,
                    lineHeight = 26.sp,
                ),
                cursorBrush = SolidColor(ColorTextTitle),
                decorationBox = { inner ->
                    if (uiState.body.isEmpty()) {
                        Text("Type here...", fontSize = 16.sp, color = ColorTextHint)
                    }
                    inner()
                },
            )

            // ── 格式工具栏 ────────────────────────────────────────────────
            if (imeVisible) {
                FormattingToolbar()
            }
        }

        // ── BottomSheet ───────────────────────────────────────────────────
        if (uiState.showTagPicker) {
            TagPickerSheet(
                availableTags = uiState.availableTags,
                selectedTags = uiState.selectedTags,
                onTagToggle = { onEvent(CreateEvent.TagToggled(it)) },
                onNewTag = { onEvent(CreateEvent.NewTagCreated(it)) },
                onDismiss = { onEvent(CreateEvent.DismissTagPicker) },
            )
        }

        if (uiState.showFolderPicker) {
            FolderPickerSheet(
                folders = uiState.availableFolders,
                selectedFolder = uiState.selectedFolder,
                onFolderSelect = { onEvent(CreateEvent.FolderSelected(it)) },
                onDismiss = { onEvent(CreateEvent.DismissFolderPicker) },
            )
        }
    }
}

// ─── Meta Chip ────────────────────────────────────────────────────────────────

@Composable
private fun MetaChip(
    iconResId: Int,
    label: String,
    isActive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val primary = Color(0xFF3D7A5A)
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isActive) primary.copy(alpha = 0.1f) else ColorChipBg,
        shadowElevation = 1.dp,
        modifier = if (onClick != null) Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            onClick = onClick,
        ) else Modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                tint = if (isActive) primary else ColorTextSub,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                fontSize = 13.sp,
                color = if (isActive) primary else ColorTextSub,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}

// ─── 格式工具栏 ───────────────────────────────────────────────────────────────

@Composable
private fun FormattingToolbar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(50),
            color = ColorChipBg,
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbarIcon(R.drawable.ic_mic, "Voice")
                ToolbarIcon(R.drawable.ic_attach, "Attach")
                ToolbarIcon(R.drawable.ic_magic, "Magic")
                ToolbarTextBtn("B", FontWeight.ExtraBold)
                ToolbarTextBtn("I", FontWeight.Bold, fontStyle = FontStyle.Italic)
                ToolbarIcon(R.drawable.ic_format_list, "List")
            }
        }
        Surface(shape = CircleShape, color = ColorChipBg, shadowElevation = 2.dp) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false),
                        onClick = {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard_hide),
                    contentDescription = "Hide keyboard",
                    tint = ColorTextTitle,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolbarIcon(iconResId: Int, contentDescription: String) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = contentDescription,
            tint = ColorTextTitle,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ToolbarTextBtn(
    text: String,
    fontWeight: FontWeight,
    fontStyle: FontStyle = FontStyle.Normal,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 16.sp, fontWeight = fontWeight, fontStyle = fontStyle, color = ColorTextTitle)
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CreateScreenPreview() {
    AppTheme {
        CreateScreen(
            uiState = CreateUiState(),
            onEvent = {},
        )
    }
}
