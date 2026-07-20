package com.novamind.app.feature.recyclebin.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.novamind.app.common.config.AppConfig
import com.novamind.app.feature.create.model.NoteItem
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
// 剩余天数文案色（Figma orange-900 #603812），与 Library 卡片日期同色
private val ColorDaysLeft: Color
    @Composable @ReadOnlyComposable get() = TextColors.Warning.onSurface.current()

/** 软删后剩余天数（保留期 [AppConfig.RecycleBin.RETENTION_DAYS] 天，从软删时间 updatedAt 起算），范围 0..保留期。 */
internal fun daysLeftUntilPurge(deletedAt: Long, now: Long = System.currentTimeMillis()): Int {
    val retentionDays = AppConfig.RecycleBin.RETENTION_DAYS
    val elapsedDays = ((now - deletedAt) / 86_400_000L).toInt()
    return (retentionDays - elapsedDays).coerceIn(0, retentionDays)
}

/**
 * 回收站瀑布流笔记卡片（Figma）：随内容高度自适应。
 * 顶部可选缩略图 → 可选标题 → 可选摘要 → 底部「剩余 N 天」（orange-900）。
 * 与 Library 卡片同款外观，仅把底部日期换成「距彻底删除还剩多少天」。
 */
@Composable
internal fun RecycleBinNoteCard(note: NoteItem, onClick: () -> Unit = {}) {
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

            // 底部：距彻底删除的剩余天数（副标题已说明「shows the days left」）
            val days = daysLeftUntilPurge(note.updatedAt)
            Text(
                text = "$days ${if (days == 1) "day" else "days"} left",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ColorDaysLeft,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F2EC)
@Composable
private fun RecycleBinNoteCardPreview() {
    AppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RecycleBinNoteCard(
                note = NoteItem(
                    id = "1",
                    title = "Q3 KPIs",
                    preview = "Discussed Q3 KPIs. John to finalize the report by Thursday.",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            RecycleBinNoteCard(
                note = NoteItem(
                    id = "2",
                    title = "Q3 KPIs",
                    preview = "",
                    updatedAt = System.currentTimeMillis() - 25L * 86_400_000L,
                ),
            )
            RecycleBinNoteCard(
                note = NoteItem(
                    id = "3",
                    title = "Pic notes",
                    preview = "",
                    imagePath = "preview/sample.jpg",
                    updatedAt = System.currentTimeMillis() - 29L * 86_400_000L,
                ),
            )
        }
    }
}
