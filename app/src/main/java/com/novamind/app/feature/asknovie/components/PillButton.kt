package com.novamind.app.feature.asknovie.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.theme.AppTheme

/**
 * 通栏胶囊按钮：[filled] = true 深色实心（禁用降透明度），false 描边。
 * 供 Rename 等弹窗的 Save / Cancel 按钮复用。
 */
@Composable
internal fun PillButton(text: String, filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg = if (filled) DarkPill else Color.Transparent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .then(
                if (filled) Modifier.background(if (enabled) bg else bg.copy(alpha = 0.4f))
                else Modifier.border(1.5.dp, DarkPill, RoundedCornerShape(26.dp)),
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (filled) OnDarkPill else DarkPill,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF7, name = "AskNovie · Pill button")
@Composable
private fun PillButtonPreview() {
    AppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PillButton(text = "Save", filled = true, enabled = true, onClick = {})
            PillButton(text = "Save", filled = true, enabled = false, onClick = {})
            PillButton(text = "Cancel", filled = false, enabled = true, onClick = {})
        }
    }
}
