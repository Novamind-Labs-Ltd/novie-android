package com.novamind.app.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.note.NoteItem

private val ColorTextTitle = Color(0xFF1A1A1A)
private val ColorTextSub = Color(0xFF6B6B6B)
private val ColorBorder = Color(0xFFE3E0D8)

/**
 * 左侧抽屉：顶部为最近笔记（chevron + 标题），底部固定 Tag manager / Shared with me / Recycle Bin。
 * 宽度约屏宽 82%，白底；点击左上角按钮或从左边缘右滑打开。
 */
@Composable
internal fun LibraryDrawer(
    notes: List<NoteItem>,
    onOpenNote: (String) -> Unit,
    onOpenTagManager: () -> Unit,
    onOpenSharedWithMe: () -> Unit,
    onOpenRecycleBin: () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = Color.White,
        drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
        modifier = Modifier.fillMaxWidth(0.82f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(vertical = 24.dp),
        ) {
            // 顶部：最近笔记（最多 8 条）
            notes.take(8).forEach { note ->
                DrawerNoteItem(title = note.title, onClick = { onOpenNote(note.id) })
            }

            Spacer(Modifier.weight(1f))

            HorizontalDivider(
                color = ColorBorder,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            DrawerActionItem(R.drawable.ic_tag, "Tag manager", onClick = onOpenTagManager)
            DrawerActionItem(R.drawable.ic_link, "Shared with me", onClick = onOpenSharedWithMe)
            DrawerActionItem(R.drawable.ic_delete, "Recycle Bin", onClick = onOpenRecycleBin)
        }
    }
}

/** 抽屉的最近笔记项：左侧 chevron + 标题。 */
@Composable
private fun DrawerNoteItem(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = ColorTextSub,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = title,
            fontSize = 16.sp,
            color = ColorTextTitle,
            maxLines = 1,
        )
    }
}

/** 抽屉底部操作项：图标 + 标签。 */
@Composable
private fun DrawerActionItem(iconRes: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = ColorTextTitle,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            fontSize = 16.sp,
            color = ColorTextTitle,
        )
    }
}
