package com.novamind.app.feature.create.folder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.feature.create.folder.components.CreateFolderButton
import com.novamind.app.feature.create.folder.components.FolderRow
import com.novamind.app.feature.create.folder.components.FolderSearchField
import com.novamind.app.feature.create.folder.components.SheetBg
import com.novamind.app.feature.create.folder.components.TextDark
import com.novamind.app.feature.create.folder.components.TextHint
import com.novamind.app.ui.theme.AppTheme

// 配色与视觉组件在 feature/create/folder/components 包（FolderPickerColors 等），本文件只保留编排。

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
        FolderSearchField(query = query, onQueryChange = { query = it })

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
            CreateFolderButton(name = trimmed, onClick = { onNewFolder(trimmed) })
        }
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
