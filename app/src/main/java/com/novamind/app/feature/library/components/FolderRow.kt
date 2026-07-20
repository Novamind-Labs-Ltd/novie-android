package com.novamind.app.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    onRename: () -> Unit = {},
    onChangeColor: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    // 文件夹颜色：有自定义色用之；否则按名称稳定地从色板取一种，使列表多彩且一致
    val accent = ColorUtils.parseHexColor(folder.colorHex) ?: folderAccentFor(folder.name)
    var menuExpanded by remember { mutableStateOf(false) }
    // Figma：行为 #fcfaf6 淡填充、圆角 12、无描边无投影、固定 56 高
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        color = BgRow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 图标：36 圆形，文件夹色淡底 + 深色文件夹图标（颜色仅体现在圆底）；点击 → 改色
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.30f))
                    .clickable(onClick = onChangeColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_folder_line),
                    contentDescription = "Change colour",
                    tint = ColorTextTitle,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = folder.name,
                fontSize = 14.sp,
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
            // 更多：展开 Rename / Change color / Delete
            Box {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { menuExpanded = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more),
                        contentDescription = "More",
                        tint = ColorTextSub,
                        modifier = Modifier.size(16.dp),
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
