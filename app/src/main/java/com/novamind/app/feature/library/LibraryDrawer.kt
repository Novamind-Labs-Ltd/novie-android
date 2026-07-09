package com.novamind.app.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.common.config.AppConfig
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.Palette
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.ColorUtils

// 配色：统一引用 ui/colors 设计系统令牌（不使用硬编码颜色）
private val BgSheet: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val ColorTextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val ColorTextSub: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
private val ColorBorder: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.default.current()

/** 无自定义色时：按文件夹名稳定地从配置色板取一种颜色（与 FolderRow 一致）。 */
private fun folderAccentFor(name: String): Color {
    val palette = AppConfig.Folder.COLORS
    if (palette.isEmpty()) return Palette.forrest600
    val idx = ((name.hashCode() % palette.size) + palette.size) % palette.size
    return palette[idx]
}

/**
 * 左侧抽屉：顶部为文件夹列表（彩色文件夹图标 + 名称 + 笔记数），底部固定
 * Tag manager / Shared with me / Recycle Bin。
 * 宽度约屏宽 82%，白底；点击左上角按钮或从左边缘右滑打开。
 */
@Composable
internal fun LibraryDrawer(
    folders: List<LibraryFolder>,
    onOpenFolder: (String) -> Unit,
    onOpenTagManager: () -> Unit,
    onOpenSharedWithMe: () -> Unit,
    onOpenRecycleBin: () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = BgSheet,
        drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
        modifier = Modifier.fillMaxWidth(0.82f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(vertical = 24.dp),
        ) {
            // 顶部：文件夹列表（最多 8 个）
            folders.take(8).forEach { folder ->
                DrawerFolderItem(folder = folder, onClick = { onOpenFolder(folder.name) })
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

/** 抽屉的文件夹项：彩色文件夹图标 + 名称 + 笔记数。 */
@Composable
private fun DrawerFolderItem(folder: LibraryFolder, onClick: () -> Unit) {
    // 文件夹颜色：有自定义色用之；否则按名称稳定地从色板取一种（与 FolderRow 一致）
    val accent = ColorUtils.parseHexColor(folder.colorHex) ?: folderAccentFor(folder.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_folder),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = folder.name,
            fontSize = 16.sp,
            color = ColorTextTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = folder.noteCount.toString(),
            fontSize = 14.sp,
            color = ColorTextSub,
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

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LibraryDrawerPreview() {
    AppTheme {
        LibraryDrawer(
            folders = listOf(
                LibraryFolder("Work", 12),
                LibraryFolder("Personal", 5, colorHex = "#388E64"),
                LibraryFolder("Ideas", 3),
            ),
            onOpenFolder = {},
            onOpenTagManager = {},
            onOpenSharedWithMe = {},
            onOpenRecycleBin = {},
        )
    }
}

@Preview(showBackground = true, name = "Library · DrawerFolderItem")
@Composable
private fun DrawerFolderItemPreview() {
    AppTheme {
        DrawerFolderItem(folder = LibraryFolder("Work", 12), onClick = {})
    }
}

@Preview(showBackground = true, name = "Library · DrawerActionItem")
@Composable
private fun DrawerActionItemPreview() {
    AppTheme {
        DrawerActionItem(iconRes = R.drawable.ic_tag, label = "Tag manager", onClick = {})
    }
}
