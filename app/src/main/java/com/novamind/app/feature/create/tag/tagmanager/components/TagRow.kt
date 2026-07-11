package com.novamind.app.feature.create.tag.tagmanager.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.create.tag.tagmanager.TagRowItem
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.ColorUtils

/** 标签行：标签图标（点击改色）+ 名称 + 笔记数 + 更多菜单（重命名/删除），边框映射标签颜色。 */
@Composable
internal fun TagRow(
    tag: TagRowItem,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onChangeColor: () -> Unit = {},
) {
    val accent = ColorUtils.parseHexColor(tag.colorHex) ?: ColorAccent
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // 边框映射标签颜色
            .border(1.dp, accent, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 点击图标 → 选择标签颜色
            Icon(
                painter = painterResource(R.drawable.ic_tag),
                contentDescription = "Change colour",
                tint = accent,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onChangeColor)
                    .padding(6.dp),
            )
            Text(
                text = tag.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = ColorTextTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(tag.noteCount.toString(), fontSize = 14.sp, color = ColorTextSub)
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
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = BgCard,
                    shadowElevation = 8.dp,
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", fontSize = 16.sp, color = ColorTextTitle) },
                        onClick = { menuExpanded = false; onRename() },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", fontSize = 16.sp, color = IconColors.Error.default.current()) },
                        onClick = { menuExpanded = false; onDelete() },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0EFEA, name = "Tag · Tag Row")
@Composable
private fun TagRowPreview() {
    AppTheme {
        TagRow(
            tag = TagRowItem("1", "Brand Identity", "#3D7A5A", 10),
            onRename = {},
            onDelete = {},
        )
    }
}
