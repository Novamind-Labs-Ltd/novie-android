package com.novamind.app.feature.library.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

/** 文件夹「更多」下拉菜单：重命名 / 改颜色 / 删除。 */
@Composable
internal fun FolderActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onChangeColor: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = BgCard,
        shadowElevation = 8.dp,
    ) {
        DropdownMenuItem(
            text = { Text("Rename", fontSize = 16.sp, color = ColorTextTitle) },
            onClick = onRename,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        )
        DropdownMenuItem(
            text = { Text("Change color", fontSize = 16.sp, color = ColorTextTitle) },
            onClick = onChangeColor,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        )
        DropdownMenuItem(
            text = { Text("Delete", fontSize = 16.sp, color = IconColors.Error.default.current()) },
            onClick = onDelete,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

@Preview(showBackground = true, name = "Library · FolderActionsMenu")
@Composable
private fun FolderActionsMenuPreview() {
    AppTheme {
        FolderActionsMenu(
            expanded = true,
            onDismiss = {},
            onRename = {},
            onChangeColor = {},
            onDelete = {},
        )
    }
}
