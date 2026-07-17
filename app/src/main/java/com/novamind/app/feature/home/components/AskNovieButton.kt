package com.novamind.app.feature.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.theme.AppTheme

/**
 * 首页「Ask Novie」入口（home_final）：深色胶囊按钮，浮于星系星尘背景之上。
 * 星系底图为设计稿 Rectangle 287 位图素材（bg_ask_novie_galaxy）；图标为 asknovie 专属描边图标。
 */
@Composable
internal fun AskNovieButton(
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(113.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 星系星尘底图（设计 Rectangle 287），横向铺满、垂直居中裁剪。
        Image(
            painter = painterResource(id = R.drawable.bg_ask_novie_galaxy),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
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
                    painter = painterResource(id = R.drawable.ic_ask_novie),
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
