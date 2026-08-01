package com.novamind.app.feature.create.components
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.TextColors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.theme.AppTheme

/**
 * 笔记编辑页错误提示条（home_final / notes_tips）：浅红底 + 错误图标 + 提示文案。
 * 用作 [NoteContentEditor] 的吸顶（stickyHeader）：正文向上滚动时常驻顶部。
 * 背景不透明（red-50），确保下方正文滚过时被完全遮挡。
 */
@Composable
internal fun NoteTipBanner(
    message: String,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(BackgroundColors.Error.tertiary.current())   // 设计：red-50 #fdf1ec
            .padding(start = 16.dp, end = if (onDismiss == null) 16.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_warning),
                contentDescription = null,
                tint = TextColors.Error.default.current(),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = message,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = TextColors.Error.default.current(),
            )
        }
        if (onDismiss != null) {
            // 视觉图标遵循 Figma 的 24dp；点击区域扩为 56×38dp，且图标右侧仍保持 16dp。
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .clickable(role = Role.Button, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = "Dismiss",
                    tint = TextColors.Primary.default.current(),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB)
@Composable
private fun NoteTipBannerPreview() {
    AppTheme {
        NoteTipBanner(
            message = "Transcript couldn't be completed.",
            onDismiss = {},
        )
    }
}
