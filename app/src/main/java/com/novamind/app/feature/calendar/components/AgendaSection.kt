package com.novamind.app.feature.calendar.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.theme.AppTheme

/** 议程区块：小标题（含计数）+ 内容列表；空且非加载中时显示空文案。 */
@Composable
internal fun AgendaSection(
    label: String,
    count: Int,
    empty: Boolean,
    emptyText: String,
    loading: Boolean,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 8.dp),
    ) {
        Text(
            label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = ColorTextTitle,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        if (empty) {
            if (!loading) {
                Text(
                    emptyText,
                    fontSize = 13.sp,
                    color = ColorTextFaint,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

@Preview(showBackground = true, name = "Calendar · Agenda section (empty)")
@Composable
private fun AgendaSectionEmptyPreview() {
    AppTheme {
        AgendaSection(label = "Events", count = 0, empty = true, emptyText = "No events", loading = false) {}
    }
}
