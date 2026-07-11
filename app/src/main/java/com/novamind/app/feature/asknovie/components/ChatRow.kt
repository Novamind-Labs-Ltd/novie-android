package com.novamind.app.feature.asknovie.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.theme.AppTheme

/** 会话历史列表行：单行标题，超长省略。 */
@Composable
internal fun ChatRow(title: String, onClick: () -> Unit) {
    Text(
        text = title,
        fontSize = 15.sp,
        color = ItemColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(vertical = 14.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF7, name = "AskNovie · Chat row")
@Composable
private fun ChatRowPreview() {
    AppTheme {
        ChatRow(title = "Trip planning for Tokyo with a very long title that ellipsizes") {}
    }
}
