package com.novamind.app.feature.home.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novamind.app.feature.create.model.NoteItem
import com.novamind.app.ui.colors.Palette
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.TimeUtils
import java.io.File

/**
 * 首页笔记卡片：固定 160×240，标题为空时用正文充当标题（1 行），剩余正文接到下方。
 */
@Composable
internal fun NoteCard(
    note: NoteItem,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 自定义边框色优先；否则按选中/默认取色
    val customBorder = note.borderColor
    val borderColor = customBorder ?: if (note.isSelected) ColorSelectedBorder else ColorBorder
    val borderWidth = 3.dp

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(160.dp)
            .height(240.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        shadowElevation = if (note.isSelected) 3.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 更新时间：同年显示日/月/时间，跨年显示日/月/年。
            Text(
                text = TimeUtils.smart(note.updatedAt),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = ColorTextSub,
            )
            // 标题为空时：用正文作标题（限 1 行），正文区显示标题没显示完的剩余内容
            val hasTitle = note.title.isNotBlank()
            // 标题 1 行实际渲染到的字符末尾位置，用于截取剩余正文
            var titleEnd by remember(note.id, note.preview) { mutableStateOf(-1) }

            Text(
                text = if (hasTitle) note.title else note.preview,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ColorTextTitle,
                // 标题固定 1 行：有标题超出用省略号，正文作标题时直接截断（剩余内容接到下方正文区）
                maxLines = 1,
                overflow = if (hasTitle) TextOverflow.Ellipsis else TextOverflow.Clip,
                onTextLayout = { layout ->
                    if (!hasTitle) {
                        val end = layout.getLineEnd(0, visibleEnd = true)
                        if (titleEnd != end) titleEnd = end
                    }
                },
            )

            val bodyText = when {
                hasTitle -> note.preview
                titleEnd in 0 until note.preview.length ->
                    note.preview.substring(titleEnd).trimStart('\n', ' ')
                else -> ""
            }
            if (bodyText.isNotBlank()) {
                Text(
                    text = bodyText,
                    fontSize = 12.sp,
                    color = ColorTextSub,
                    lineHeight = 17.sp,
                    // 行数不写死：占满时间/标题/图片之外的剩余空间，
                    // 有图时缩略图先占位，正文可见行数随剩余高度自动减少；无图时铺满剩余高度。
                    modifier = Modifier.weight(1f),
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                // 无正文但有图片时，用弹性留白把缩略图压到底部
                Spacer(Modifier.weight(1f))
            }

            // 正文存在图片时：展示第一张缩略图（56dp 圆角方图），固定占位在底部
            note.imagePath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp)),
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
            NoteCard(
                NoteItem(
                    "1",
                    "Market research",
                    "Here is an overview of your competitors in 2026.",
                    updatedAt = System.currentTimeMillis(),
                )
            )
            NoteCard(
                NoteItem(
                    "2",
                    "",
                    "Untitled: this note uses the body text as the title, with remaining content shown below the divider.",
                    isSelected = true,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            NoteCard(
                NoteItem(
                    "3",
                    "Q3 KPIs",
                    "Discussed Q3 KPIs. John to finalize the report by Thursday.",
                    borderColor = Palette.red500,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }
}
