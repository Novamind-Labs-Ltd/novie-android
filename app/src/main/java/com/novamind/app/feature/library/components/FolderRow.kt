package com.novamind.app.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.common.config.AppConfig
import com.novamind.app.feature.library.LibraryFolder
import com.novamind.app.ui.colors.Palette
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.ColorUtils

/** 无自定义色时：按文件夹名稳定地从配置色板取一种颜色。 */
private fun folderAccentFor(name: String): Color {
    val palette = AppConfig.Folder.COLORS
    if (palette.isEmpty()) return Palette.forrest600
    val idx = ((name.hashCode() % palette.size) + palette.size) % palette.size
    return palette[idx]
}

@Composable
internal fun FolderRow(
    folder: LibraryFolder,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    elevation: androidx.compose.ui.unit.Dp = 1.dp,   // 拖拽态抬升以凸显
    onRename: () -> Unit = {},
    onChangeColor: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    // 文件夹颜色：有自定义色用之；否则按名称稳定地从色板取一种，使列表多彩且一致
    val accent = ColorUtils.parseHexColor(folder.colorHex) ?: folderAccentFor(folder.name)
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            // 边框映射文件夹颜色
            .border(1.dp, accent, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        shadowElevation = elevation,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 点击图标 → 打开 Folder colour 弹窗（与「Change color」一致）
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f))
                    .clickable(onClick = onChangeColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_folder),
                    contentDescription = "Change colour",
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = folder.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = ColorTextTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 笔记数
            Text(
                text = folder.noteCount.toString(),
                fontSize = 14.sp,
                color = ColorTextSub,
            )
            // 更多：展开 Rename / Reorder / Delete
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
                FolderActionsMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onRename = { menuExpanded = false; onRename() },
                    onChangeColor = { menuExpanded = false; onChangeColor() },
                    onDelete = { menuExpanded = false; onDelete() },
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Library · FolderRow")
@Composable
private fun FolderRowPreview() {
    AppTheme {
        FolderRow(folder = sampleFolders.first(), onClick = {})
    }
}
