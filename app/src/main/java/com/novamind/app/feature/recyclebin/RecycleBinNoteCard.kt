package com.novamind.app.feature.recyclebin

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
import com.novamind.app.feature.note.NoteItem
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import java.io.File

// RecycleBinNoteCard 配色：对齐设计系统语义令牌（ui/colors），随主题深浅自动解析
private val BgCard: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val ColorTextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val ColorTextSub: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
private val ColorBorder: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.default.current()

/** 软删后剩余天数（保留期 [RECYCLE_RETENTION_DAYS] 天，从软删时间 updatedAt 起算），范围 0..保留期。 */
internal fun daysLeftUntilPurge(deletedAt: Long, now: Long = System.currentTimeMillis()): Int {
    val elapsedDays = ((now - deletedAt) / 86_400_000L).toInt()
    return (RECYCLE_RETENTION_DAYS - elapsedDays).coerceIn(0, RECYCLE_RETENTION_DAYS)
}

/** 回收站笔记卡片：顶部「剩余 N 天」+ 标题 + 内容（占剩余空间）+ 底部缩略图。 */
@Composable
internal fun RecycleBinNoteCard(note: NoteItem, onClick: () -> Unit = {}) {
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
            // 剩余天数（替代日期）
            val days = daysLeftUntilPurge(note.updatedAt)
            Text(
                text = "$days ${if (days == 1) "day" else "days"} left",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = ColorTextSub,
            )
            // 标题为空时用正文充当标题（限 1 行），与首页 / Library 卡片一致
            val hasTitle = note.title.isNotBlank()
            Text(
                text = if (hasTitle) note.title else note.description,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ColorTextTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 内容：占满剩余空间，把缩略图压到底部
            if (note.description.isNotBlank()) {
                Text(
                    text = note.description,
                    fontSize = 12.sp,
                    color = ColorTextSub,
                    lineHeight = 17.sp,
                    modifier = Modifier.weight(1f),
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            // 底部缩略图（有图才显示）
            note.imagePath?.let { path ->
                val thumbModifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                if (LocalInspectionMode.current) {
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
private fun RecycleBinNoteCardPreview() {
    AppTheme {
        RecycleBinNoteCard(
            note = NoteItem(
                id = "1",
                title = "Q3 marketing campaign",
                description = "Meeting Summary\nQ3 Strategy: Reviewed competitor analysis and finalized the budget for the upcoming product launch.",
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}
