package com.novamind.app.feature.asknovie.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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

/**
 * 快捷建议 chip（Figma asknovie 空状态）：描边胶囊、透明底、自适应宽度，
 * 点击把建议文案作为消息发送。
 */
@Composable
internal fun SuggestionChip(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Text(
        text = text,
        color = ChipText,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(shape)
            .border(1.dp, ChipBorder, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF1EEE6, name = "AskNovie · Suggestion chip")
@Composable
private fun SuggestionChipPreview() {
    AppTheme {
        SuggestionChip(text = "Summarize my notes") {}
    }
}
