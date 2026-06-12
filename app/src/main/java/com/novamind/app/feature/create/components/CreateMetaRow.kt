package com.novamind.app.feature.create.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.create.folder.Folder
import com.novamind.app.feature.create.tag.Tag
import com.novamind.app.feature.create.tag.TagChip
import com.novamind.app.ui.theme.AppTheme

/**
 * Meta 操作行：文件夹 chip、标签 chip（已选标签 + 添加入口）、时间 chip。可横向滚动。
 */
@Composable
fun CreateMetaRow(
    selectedFolder: Folder?,
    selectedTags: List<Tag>,
    timeLabel: String,
    onShowFolderPicker: () -> Unit,
    onShowTagPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 文件夹 chip（编辑页仅显示 folder，不平铺已选 tag）
        MetaChip(
            iconResId = R.drawable.ic_nav_library,
            label = selectedFolder?.name ?: "Add to folder",
            isActive = selectedFolder != null,
            onClick = onShowFolderPicker,
        )
        // 标签入口：仅提供「新增/选择 tag」按钮，始终中性样式（不高亮选中态）
        MetaChip(
            iconResId = R.drawable.ic_nav_brand,
            label = "Tags",
            isActive = false,
            onClick = onShowTagPicker,
        )
        // 时间：不做成胶囊，只显示图标 + 文字
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_calendar),
                contentDescription = null,
                tint = ColorTextSub,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = timeLabel,
                fontSize = 13.sp,
                color = ColorTextSub,
            )
        }
    }
}

@Composable
private fun MetaChip(
    iconResId: Int,
    label: String,
    isActive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        shape = shape,
        color = if (isActive) ColorPrimary.copy(alpha = 0.1f) else ColorChipBg,
        shadowElevation = 1.dp,
        // 先按形状裁剪再 clickable，使按压 ripple 也是圆角，与 chip 形状一致
        modifier = if (onClick != null) Modifier
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ) else Modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                tint = if (isActive) ColorPrimary else ColorTextSub,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                fontSize = 13.sp,
                color = if (isActive) ColorPrimary else ColorTextSub,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFFDFCF8, name = "未选择")
@Composable
private fun CreateMetaRowEmptyPreview() {
    AppTheme {
        CreateMetaRow(
            selectedFolder = null,
            selectedTags = emptyList(),
            timeLabel = "Today 14:26",
            onShowFolderPicker = {},
            onShowTagPicker = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFDFCF8, name = "已选文件夹+标签")
@Composable
private fun CreateMetaRowFilledPreview() {
    AppTheme {
        CreateMetaRow(
            selectedFolder = Folder(id = "f1", name = "Work"),
            selectedTags = listOf(
                Tag(id = "t1", name = "Research", colorHex = "#3D7A5A"),
                Tag(id = "t3", name = "Design", colorHex = "#3D5A7A"),
            ),
            timeLabel = "Today 14:26",
            onShowFolderPicker = {},
            onShowTagPicker = {},
        )
    }
}
