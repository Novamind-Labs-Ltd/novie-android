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
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

/**
 * 通用返回按钮：圆形白底带阴影 + 返回箭头，无界波纹。
 * 供 Create / Library / 各导航子页等复用，保证返回键样式统一。
 *
 * 配色默认取 ui/colors 设计系统令牌（@Composable 默认参数在组合上下文求值），随主题深浅自动解析。
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
    background: Color = BackgroundColors.Surface.default.current(),
    tint: Color = IconColors.Default.default.current(),
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

/** 笔记编辑页使用的扁平顶栏返回按钮：36dp 命中区、24dp 图标、无背景。 */
@Composable
fun TopBarBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Back",
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 18.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_arrow_back),
            contentDescription = contentDescription,
            tint = IconColors.Default.default.current(),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0EFEA)
@Composable
private fun BackButtonPreview() {
    AppTheme {
        BackButton(onClick = {}, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0EFEA)
@Composable
private fun TopBarBackButtonPreview() {
    AppTheme {
        TopBarBackButton(onClick = {}, modifier = Modifier.padding(16.dp))
    }
}
