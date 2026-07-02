package com.novamind.app.feature.asknovie.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.theme.AppTheme

/** 右上角「更多」下拉菜单：Share / Rename / Export to notes / Delete。 */
@Composable
internal fun MoreMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onExportToNotes: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = MenuBg,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 12.dp,
        tonalElevation = 0.dp,
        modifier = Modifier.width(220.dp),
    ) {
        MoreMenuItem("Share", onShare)
        MoreMenuItem("Rename", onRename)
        MoreMenuItem("Export to notes", onExportToNotes)
        MoreMenuItem("Delete", onDelete)
    }
}

@Composable
private fun MoreMenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = TextTitle, fontSize = 16.sp) },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    )
}

// DropdownMenu 是弹窗层，静态预览不渲染；预览菜单项本体验证样式。
@Preview(showBackground = true, backgroundColor = 0xFFF4F2EA, name = "AskNovie · 更多菜单项")
@Composable
private fun MoreMenuItemPreview() {
    AppTheme {
        Column {
            MoreMenuItem("Share") {}
            MoreMenuItem("Rename") {}
            MoreMenuItem("Export to notes") {}
            MoreMenuItem("Delete") {}
        }
    }
}
