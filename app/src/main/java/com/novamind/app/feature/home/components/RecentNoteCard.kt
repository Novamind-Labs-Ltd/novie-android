package com.novamind.app.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.TimeUtils
import java.io.File
import java.util.Locale

/**
 * 首页「Recent notes」纵向全幅卡片（content_notes）：标题（可选）+ 摘要（可选）+ 日期/时间；
 * 正文含图时右侧展示缩略图。多条时随整页纵向滚动。
 */
@Composable
internal fun RecentNoteCard(
    note: NoteItem,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = BgCard,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 含图时用 IntrinsicSize.Min 让缩略图高度跟随左侧内容高度（Figma：图片充满卡片高度）
                .then(if (note.imagePath != null) Modifier.height(IntrinsicSize.Min) else Modifier)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (note.title.isNotBlank()) {
                    Text(
                        text = note.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ColorTextTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (note.preview.isNotBlank()) {
                    Text(
                        text = note.preview,
                        fontSize = 14.sp,
                        color = ColorTextSub,
                        lineHeight = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // ── 日期 · 时间（AUG 1 · 10:00AM）───────────────────────────
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val stamp = note.updatedAt.takeIf { it > 0L } ?: note.createdAt
                    if (stamp > 0L) {
                        Text(
                            text = TimeUtils.format(stamp, "MMM d", Locale.ENGLISH).uppercase(Locale.ENGLISH),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = ColorTimeStamp,
                        )
                        Text(
                            text = TimeUtils.format(stamp, "h:mma", Locale.ENGLISH),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = ColorTimeStamp,
                        )
                    }
                }
            }

            // 正文首图缩略图（本地路径，Coil 加载）；无图则不渲染。
            // Figma：宽约 100dp、圆角 8、充满卡片高度（设最小高度避免内容过短时缩略图过小）。
            note.imagePath?.let { path ->
                AsyncImage(
                    // 本地文件路径用 File 加载；远端签名 URL（本地失效时的兜底）直接传字符串
                    model = if (path.startsWith("http")) path else File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(100.dp)
                        .heightIn(min = 96.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}

// 涵盖：标题+摘要 / 仅摘要 / 仅标题 / 带缩略图 四种形态。
@Composable
private fun RecentNoteCardShowcase() {
    val now = System.currentTimeMillis()
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RecentNoteCard(
            NoteItem("1", "Q3 KPIs", "Discussed Q3 KPIs. John to finalize the report by Thursday.", updatedAt = now),
        )
        RecentNoteCard(
            NoteItem("2", "", "Discussed Q3 KPIs. John to finalize the report by Thursday.", updatedAt = now),
        )
        RecentNoteCard(
            NoteItem("3", "Q3 KPIs", "", updatedAt = now),
        )
        RecentNoteCard(
            NoteItem("4", "Pic notes", "Team offsite venue shortlist and logistics.", imagePath = "/preview/none.jpg", updatedAt = now),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Home · Recent Note Card")
@Composable
private fun RecentNoteCardPreview() {
    AppTheme { RecentNoteCardShowcase() }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A, name = "Home · Recent Note Card (Dark)")
@Composable
private fun RecentNoteCardDarkPreview() {
    AppTheme(darkTheme = true) { RecentNoteCardShowcase() }
}
