package com.novamind.app.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
import com.novamind.app.feature.library.components.folderAccentFor
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.ColorUtils

// 配色：统一引用 ui/colors 设计系统令牌（不使用硬编码颜色）
// Figma「Side menu」底色 background/page/default/secondary（#fcfaf6）
private val BgSheet: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.secondary.current()
private val ColorTextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val ColorTextSub: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
private val ColorBorder: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.default.current()

/**
 * 左侧抽屉（Figma「Side menu」）：顶部「Folders」标题 + 文件夹列表（彩色文件夹图标 +
 * 名称 + 笔记数），底部固定「Recycle Bin」。点击左上角侧栏按钮或从左边缘右滑打开。
 *
 * 注：[onOpenTagManager] / [onOpenSharedWithMe] 保留在签名中以兼容调用方，本方案暂不在
 * 抽屉里提供入口（后续如需可恢复对应 DrawerActionItem）。
 */
@Composable
internal fun LibraryDrawer(
    folders: List<LibraryFolder>,
    onOpenFolder: (String) -> Unit,
    onOpenTagManager: () -> Unit,
    onOpenSharedWithMe: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 侧边菜单基底（push-reveal 的底层）：铺满屏幕、#fcfaf6 底；
    // 内容占左侧约 72%，右侧由被推开的主内容卡片盖住。
    Box(modifier = modifier.fillMaxSize().background(BgSheet)) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.72f)
                .statusBarsPadding()
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            // 标题「Folders」（Figma H3：28sp SemiBold）
            Text(
                text = "Folders",
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = ColorTextTitle,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 14.dp, bottom = 12.dp),
            )
            // 文件夹列表（占据剩余空间，底部操作项固定）
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(folders, key = { it.name }) { folder ->
                    DrawerFolderItem(folder = folder, onClick = { onOpenFolder(folder.name) })
                }
            }

            HorizontalDivider(
                color = ColorBorder,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            )

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
            .padding(horizontal = 28.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 与文件夹列表 / 重命名行统一：文件夹色 30% 淡底圆 + 深色描边文件夹图标（颜色只体现在圆底）。
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.30f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_folder_line),
                contentDescription = null,
                tint = ColorTextTitle,
                modifier = Modifier.size(20.dp),
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
            .padding(horizontal = 28.dp, vertical = 12.dp),
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
