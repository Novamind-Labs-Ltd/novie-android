package com.novamind.app.feature.create.folder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

// 配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析（不使用硬编码颜色）
private val Primary: Color
    @Composable @ReadOnlyComposable get() = IconColors.Brand.default.current()
private val TextDark: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val TextHint: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.tertiary.current()
private val SheetBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
private val ItemBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val RadioUnselected: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.tertiary.current()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    folders: List<Folder>,
    selectedFolder: Folder?,
    onFolderSelect: (Folder?) -> Unit,
    onNewFolder: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        FolderPickerContent(
            folders = folders,
            selectedFolder = selectedFolder,
            onFolderSelect = onFolderSelect,
            onNewFolder = onNewFolder,
            // 固定高度：占屏幕约 85%，不随文件夹数量伸缩
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
        )
    }
}

/** 文件夹选择的纯内容（与 sheet 容器解耦，便于 @Preview / 复用）。 */
@Composable
private fun FolderPickerContent(
    folders: List<Folder>,
    selectedFolder: Folder?,
    onFolderSelect: (Folder?) -> Unit,
    onNewFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val filtered = remember(query, folders) {
        if (trimmed.isEmpty()) folders
        else folders.filter { it.name.contains(trimmed, ignoreCase = true) }
    }
    // 没有同名文件夹时，允许「创建」
    val canCreate = trimmed.isNotEmpty() && folders.none { it.name.equals(trimmed, ignoreCase = true) }

    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 标题
        Text(
            text = "Select folder",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        // 搜索 / 新建输入框
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = ItemBg,
            shadowElevation = 1.dp,
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = TextDark),
                cursorBrush = SolidColor(TextDark),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("Search or create new folder", fontSize = 15.sp, color = TextHint)
                    }
                    inner()
                },
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // 未搜索时提供「No folder」清除项
            if (trimmed.isEmpty()) {
                FolderRow(
                    name = "No folder",
                    isSelected = selectedFolder == null,
                    onClick = { onFolderSelect(null) },
                )
            }
            filtered.forEach { folder ->
                FolderRow(
                    name = folder.name,
                    isSelected = selectedFolder?.id == folder.id,
                    onClick = { onFolderSelect(folder) },
                )
            }
            if (filtered.isEmpty() && !canCreate) {
                Text(
                    text = "No matching folder",
                    fontSize = 13.sp,
                    color = TextHint,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                )
            }
        }

        // 无精准匹配时，底部显示「创建新文件夹」按钮
        if (canCreate) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = { onNewFolder(trimmed) },
                    ),
                shape = RoundedCornerShape(50),
                color = Color.Transparent,
                border = BorderStroke(1.dp, TextDark),
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("+", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = TextDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Create new folder ‘$trimmed’",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark,
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = Primary,
                unselectedColor = RadioUnselected,
            ),
        )
        Text(
            text = name,
            fontSize = 16.sp,
            color = if (isSelected) Primary else TextDark,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

private val previewFolders = listOf(
    Folder(id = "f1", name = "Work"),
    Folder(id = "f2", name = "Personal"),
    Folder(id = "f3", name = "Projects"),
)

@Preview(showBackground = true, backgroundColor = 0xFFF0EFEA)
@Composable
private fun FolderPickerContentPreview() {
    var selected by remember { mutableStateOf<Folder?>(previewFolders.firstOrNull()) }
    AppTheme {
        Surface(color = SheetBg) {
            FolderPickerContent(
                folders = previewFolders,
                selectedFolder = selected,
                onFolderSelect = { selected = it },
                onNewFolder = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp),
            )
        }
    }
}
