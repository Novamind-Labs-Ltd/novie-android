package com.novamind.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novamind.app.R
import com.novamind.app.ui.theme.AppTheme

/**
 * 通用返回按钮：圆形白底带阴影 + 返回箭头，无界波纹。
 * 供 Create / Library / 各导航子页等复用，保证返回键样式统一。
 *
 * @param onClick 点击返回回调
 * @param size 命中区直径
 * @param iconSize 箭头尺寸
 * @param background 圆形底色
 * @param tint 箭头颜色
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    background: Color = Color.White,
    tint: Color = Color(0xFF1A1A1A),
    contentDescription: String = "Back",
) {
    Surface(
        shape = CircleShape,
        color = background,
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_back),
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0EFEA)
@Composable
private fun BackButtonPreview() {
    AppTheme {
        BackButton(onClick = {}, modifier = Modifier.padding(16.dp))
    }
}
