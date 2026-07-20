package com.novamind.app.feature.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalInspectionMode
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
 * 列表态笔记项（Figma）：整宽卡片，左侧标题/摘要/日期，右侧可选缩略图（充满卡片高度）。
 * 圆角 12、白底 + 轻投影；仅设置了自定义边框色时描边。
 */
@Composable
internal fun LibraryNoteRow(note: NoteItem, onClick: () -> Unit = {}) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = BgCard,
        shadowElevation = 1.dp,
        border = note.borderColor?.let { BorderStroke(1.dp, it) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 含图时用 IntrinsicSize.Min 让缩略图高度跟随左侧内容
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
                val stamp = note.updatedAt.takeIf { it > 0L } ?: note.createdAt
                if (stamp > 0L) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
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

            // 右侧缩略图（正文首图；无图不渲染），约 100dp 宽、圆角 8、充满卡片高度
            note.imagePath?.let { path ->
                val imgModifier = Modifier
                    .width(100.dp)
                    .heightIn(min = 96.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                if (LocalInspectionMode.current) {
                    Box(modifier = imgModifier.background(ColorBorder))
                } else {
                    AsyncImage(
                        model = if (path.startsWith("http")) path else File(path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = imgModifier,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Library · LibraryNoteRow")
@Composable
private fun LibraryNoteRowPreview() {
    AppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LibraryNoteRow(
                note = NoteItem(
                    id = "1",
                    title = "",
                    preview = "Discussed Q3 KPIs. John to finalize the report by Thursday.",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            LibraryNoteRow(
                note = NoteItem(
                    id = "2",
                    title = "Q3 KPIs",
                    preview = "Discussed Q3 KPIs. John to finalize the report by Thursday.",
                    imagePath = "preview/sample.jpg",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            LibraryNoteRow(
                note = NoteItem(id = "3", title = "Q3 KPIs", preview = "", updatedAt = System.currentTimeMillis()),
            )
        }
    }
}
