package com.novamind.app.feature.home

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.theme.AppTheme

// NoteCard 自用配色（与 HomeScreen 同值；遵循 home 模块按文件私有配色的现状）
private val BgCard = Color(0xFFFFFFFF)
private val ColorTextTitle = Color(0xFF1A1A1A)
private val ColorTextSub = Color(0xFF6B6B6B)
private val ColorBorder = Color(0xFFE0E0E0)
private val ColorSelectedBorder = Color(0xFFAAD4C8)

/**
 * 首页笔记卡片：固定 160×120，标题为空时用正文充当标题（1 行），剩余正文接到下方。
 */
@Composable
internal fun NoteCard(
    note: NoteItem,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val borderColor = if (note.isSelected) ColorSelectedBorder else ColorBorder
    val borderWidth = if (note.isSelected) 1.5.dp else 1.dp

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(160.dp)
            .height(120.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        shadowElevation = if (note.isSelected) 3.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (note.folderName != null) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF3D7A5A).copy(alpha = 0.08f),
                ) {
                    Text(
                        text = "📁 ${note.folderName}",
                        fontSize = 10.sp,
                        color = Color(0xFF3D7A5A),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
            // 标题为空时：用正文作标题（限 1 行），正文区显示标题没显示完的剩余内容
            val hasTitle = note.title.isNotBlank()
            // 标题 1 行实际渲染到的字符末尾位置，用于截取剩余正文
            var titleEnd by remember(note.id, note.description) { mutableStateOf(-1) }

            Text(
                text = if (hasTitle) note.title else note.description,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ColorTextTitle,
                maxLines = if (hasTitle) 2 else 1,
                // 正文作标题时直接截断、不加省略号（剩余内容会接到下方正文区）
                overflow = if (hasTitle) TextOverflow.Ellipsis else TextOverflow.Clip,
                onTextLayout = { layout ->
                    if (!hasTitle) {
                        val end = layout.getLineEnd(0, visibleEnd = true)
                        if (titleEnd != end) titleEnd = end
                    }
                },
            )
            HorizontalDivider(Modifier, thickness = 0.8.dp, color = ColorBorder)

            val bodyText = when {
                hasTitle -> note.description
                titleEnd in 0 until note.description.length ->
                    note.description.substring(titleEnd).trimStart('\n', ' ')
                else -> ""
            }
            if (bodyText.isNotBlank()) {
                Text(
                    text = bodyText,
                    fontSize = 12.sp,
                    color = ColorTextSub,
                    lineHeight = 17.sp,
                    maxLines = 4,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0EFEA)
@Composable
private fun NoteCardPreview() {
    AppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NoteCard(NoteItem("1", "Market research", "Here is an overview of your competitors in 2026."))
            NoteCard(NoteItem("2", "", "无标题：这条用正文充当标题，剩余内容会接到分割线下方继续展示。", isSelected = true))
        }
    }
}
