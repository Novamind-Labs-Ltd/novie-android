package com.novamind.app.feature.library.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.feature.create.model.NoteItem
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.TimeUtils

/** 文件夹详情页的笔记项：日期 + 标题 + 预览（整宽卡片）。 */
@Composable
internal fun FolderNoteRow(note: NoteItem, onClick: () -> Unit) {
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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = TimeUtils.smart(note.updatedAt),
                fontSize = 12.sp,
                color = ColorTextSub,
            )
            Text(note.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
            Text(
                note.description,
                fontSize = 14.sp,
                color = ColorTextTitle.copy(alpha = 0.8f),
                lineHeight = 20.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true, name = "Library · FolderNoteRow")
@Composable
private fun FolderNoteRowPreview() {
    AppTheme {
        FolderNoteRow(note = sampleNotes.first(), onClick = {})
    }
}
