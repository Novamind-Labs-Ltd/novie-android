package com.novamind.app.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
import java.util.Locale

// LibraryNoteCard 配色：对齐设计系统语义令牌（ui/colors），随主题深浅自动解析
private val BgCard: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val ColorTextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val ColorTextSub: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
private val ColorBorder: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.default.current()
// 日期文案色（Figma orange-900 #603812）
private val ColorDate: Color
    @Composable @ReadOnlyComposable get() = TextColors.Warning.onSurface.current()

/**
 * 瀑布流笔记卡片（Figma）：随内容高度自适应。
 * 顶部可选缩略图 → 可选标题 → 可选摘要 → 底部日期（AUG 1 · 10:00AM）。
 * 仅在设置了自定义边框色时描边；否则为无边框白卡。
 */
@Composable
internal fun LibraryNoteCard(note: NoteItem, onClick: () -> Unit = {}) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = BgCard,
        shadowElevation = 1.dp,
        border = note.borderColor?.let { BorderStroke(1.dp, it) },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 顶部缩略图（正文首图；无图不渲染）
            note.imagePath?.let { path ->
                val imgModifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                if (LocalInspectionMode.current) {
                    Box(modifier = imgModifier.background(ColorBorder))
                } else {
                    AsyncImage(
                        // 本地文件路径用 File 加载；远端签名 URL 直接传字符串
                        model = if (path.startsWith("http")) path else File(path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = imgModifier,
                    )
                }
            }

            // 标题 + 摘要（各自可选）
            if (note.title.isNotBlank() || note.preview.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (note.title.isNotBlank()) {
                        Text(
                            text = note.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ColorTextTitle,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (note.preview.isNotBlank()) {
                        Text(
                            text = note.preview,
                            fontSize = 13.sp,
                            color = ColorTextSub,
                            lineHeight = 20.sp,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 日期 · 时间（AUG 1 · 10:00AM）
            val stamp = note.updatedAt.takeIf { it > 0L } ?: note.createdAt
            if (stamp > 0L) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = TimeUtils.format(stamp, "MMM d", Locale.ENGLISH).uppercase(Locale.ENGLISH),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorDate,
                    )
                    Text(
                        text = TimeUtils.format(stamp, "h:mma", Locale.ENGLISH),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorDate,
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
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LibraryNoteCard(
                note = NoteItem(
                    id = "1",
                    title = "",
                    preview = "Discussed Q3 KPIs. John to finalize the report by Thursday.",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            LibraryNoteCard(
                note = NoteItem(
                    id = "2",
                    title = "Q3 KPIs",
                    preview = "",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            LibraryNoteCard(
                note = NoteItem(
                    id = "3",
                    title = "Pic notes",
                    preview = "Discussed Q3 KPIs. John to finalize the report by Thursday.",
                    imagePath = "preview/sample.jpg",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}
