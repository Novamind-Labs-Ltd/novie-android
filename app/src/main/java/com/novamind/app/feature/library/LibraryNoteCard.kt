package com.novamind.app.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novamind.app.feature.create.model.NoteItem
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.TimeUtils
import java.io.File

// LibraryNoteCard 配色：对齐设计系统语义令牌（ui/colors），随主题深浅自动解析
private val BgCard: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val ColorTextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val ColorTextSub: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
private val ColorBorder: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.default.current()

/** 网格态笔记卡片：日期 + 标题 + 内容（占剩余空间）+ 底部缩略图。 */
@Composable
internal fun LibraryNoteCard(note: NoteItem, onClick: () -> Unit = {}) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(172.dp)
            .height(180.dp)
            .border(3.dp, note.borderColor ?: ColorBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 日期
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

            // 内容：占满标题与图片之外的剩余空间
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
                    modifier = Modifier.weight(1f),
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                // 无正文但有图片时，用弹性留白把缩略图压到底部
                Spacer(Modifier.weight(1f))
            }
            // 底部缩略图（有图才显示）
            note.imagePath?.let { path ->
                val thumbModifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                if (LocalInspectionMode.current) {
                    // 预览态：File 无法加载，用占位色块呈现「有图」效果
                    Box(modifier = thumbModifier.background(ColorBorder))
                } else {
                    AsyncImage(
                        model = File(path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = thumbModifier,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F2EC)
@Composable
private fun LibraryNoteCardPreview() {
    AppTheme {
        LibraryNoteCard(
            note = NoteItem(
                id = "1",
                title = "Product roadmap",
                preview = "Discussed Q3 KPIs. John to finalize the report by Thursday.",
                tags = listOf("Work"),
                folderName = "Work",
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F2EC)
@Composable
private fun LibraryNoteCardWithImagePreview() {
    AppTheme {
        LibraryNoteCard(
            note = NoteItem(
                id = "2",
                title = "Product roadmap",
                preview = "Discussed Q3 KPIs. John to finalize the report by Thursday.",
                tags = listOf("Work"),
                folderName = "Work",
                updatedAt = System.currentTimeMillis(),
                imagePath = "preview/sample.jpg",
            ),
        )
    }
}
