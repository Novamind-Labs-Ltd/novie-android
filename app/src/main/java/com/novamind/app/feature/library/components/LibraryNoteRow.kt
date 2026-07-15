package com.novamind.app.feature.library.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.feature.create.model.NoteItem
import com.novamind.app.ui.theme.AppTheme

/** 列表态笔记项：整宽横向卡片（标签 + 标题 + 预览）。 */
@Composable
internal fun LibraryNoteRow(note: NoteItem, onClick: () -> Unit = {}) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ColorBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = RoundedCornerShape(50), color = ColorAccent.copy(alpha = 0.1f)) {
                    Text(
                        text = note.tags.firstOrNull() ?: note.folderName ?: "Note",
                        fontSize = 10.sp,
                        color = ColorAccent,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Text(
                    note.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextTitle,
                )
            }
            Text(
                note.preview,
                fontSize = 13.sp,
                color = ColorTextSub,
                lineHeight = 18.sp,
                maxLines = 2,
            )
        }
    }
}

@Preview(showBackground = true, name = "Library · LibraryNoteRow")
@Composable
private fun LibraryNoteRowPreview() {
    AppTheme {
        LibraryNoteRow(note = sampleNotes.first(), onClick = {})
    }
}
