package com.novamind.app.feature.create.components
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.BackgroundColors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.create.folder.Folder
import com.novamind.app.feature.create.tag.Tag
import com.novamind.app.ui.theme.AppTheme

/**
 * Meta 操作行（home_final / new conversation）：文件夹 chip、标签 chip、日期时间。可横向滚动。
 * 设计：米色扁平胶囊（#fcfaf6，无阴影），文件夹/标签图标 16dp、文字 14sp；日期图标 16dp、文字 12sp。
 */
@Composable
fun CreateMetaRow(
    selectedFolder: Folder?,
    selectedTags: List<Tag>,
    timeLabel: String,
    onShowFolderPicker: () -> Unit,
    onShowTagPicker: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,   // 回收站只读态：chip 仅展示、不可点击
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 文件夹 + 标签 两枚胶囊为一组（组内间距 10）
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 文件夹：无文件夹时显示「Unassigned」（对齐设计稿）
            MetaChip(
                iconResId = R.drawable.ic_folder,
                label = selectedFolder?.name ?: "Unassigned",
                onClick = if (readOnly) null else onShowFolderPicker,
            )
            // 标签入口暂隐藏（保留参数与选中态,后续可恢复）
        }
        // 日期时间：非胶囊，图标 16 + 文字 12
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_calendar),
                contentDescription = null,
                tint = TextColors.Primary.secondary.current(),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = timeLabel,
                fontSize = 12.sp,
                color = TextColors.Primary.secondary.current(),
            )
        }
    }
}

/** 标签数量显示：超过 99 显示「99+」，否则原样。 */
private fun tagCountLabel(count: Int): String = if (count > 99) "99+" else count.toString()

@Composable
private fun MetaChip(
    iconResId: Int,
    label: String,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(100)
    Surface(
        shape = shape,
        color = BackgroundColors.Page.secondary.current(),   // 设计：米色 #fcfaf6，无阴影
        // 先按形状裁剪再 clickable，使按压 ripple 也是圆角
        modifier = if (onClick != null) Modifier
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ) else Modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                tint = TextColors.Primary.default.current(),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                fontSize = 14.sp,
                color = TextColors.Primary.default.current(),
            )
        }
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Not Selected")
@Composable
private fun CreateMetaRowEmptyPreview() {
    AppTheme {
        CreateMetaRow(
            selectedFolder = null,
            selectedTags = emptyList(),
            timeLabel = "Today 8:31 am",
            onShowFolderPicker = {},
            onShowTagPicker = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Folder + Tag Selected")
@Composable
private fun CreateMetaRowFilledPreview() {
    AppTheme {
        CreateMetaRow(
            selectedFolder = Folder(id = "f1", name = "Work"),
            selectedTags = listOf(
                Tag(id = "t1", name = "Research", colorHex = "#3D7A5A"),
                Tag(id = "t3", name = "Design", colorHex = "#3D5A7A"),
            ),
            timeLabel = "Today 8:31 am",
            onShowFolderPicker = {},
            onShowTagPicker = {},
        )
    }
}
