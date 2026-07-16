package com.novamind.app.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.colors.Palette
import com.novamind.app.ui.theme.AppTheme

/**
 * 首页「Ask Novie」入口（home_final）：深色胶囊按钮，浮于星系背景之上。
 *
 * 注意：设计稿的星系底图（Rectangle 287）为位图素材，当前环境无法下载，
 * 暂用轻微径向渐变占位（TODO: 接入真实素材 asknovie_galaxy.png 后替换）。
 * 图标同样用项目内 ic_chat 占位（设计为 asknovie 专属图标）。
 */
@Composable
internal fun AskNovieButton(
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // TODO(assets): 星系底图占位 —— 待 asknovie 位图素材可下载后替换为 Image。
    val galaxyPlaceholder = Brush.radialGradient(
        colors = listOf(Palette.neutral100, Palette.neutral50.copy(alpha = 0f)),
        center = Offset.Unspecified,
        radius = 520f,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(113.dp)
            .background(galaxyPlaceholder),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(100.dp),
            color = ColorButtonDark,
        ) {
            Row(
                modifier = Modifier
                    .height(56.dp)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chat),
                    contentDescription = null,
                    tint = ColorOnDark,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "Ask Novie",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorOnDark,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Home · Ask Novie")
@Composable
private fun AskNovieButtonPreview() {
    AppTheme {
        AskNovieButton()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A, name = "Home · Ask Novie (Dark)")
@Composable
private fun AskNovieButtonDarkPreview() {
    AppTheme(darkTheme = true) {
        AskNovieButton()
    }
}
