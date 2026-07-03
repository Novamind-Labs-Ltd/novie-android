package com.novamind.app.feature.recyclebin.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.novamind.app.R
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

/** 回收站顶栏图标按钮：卡片底 + 自定形状（返回 / 更多）。 */
@Composable
internal fun TopIconButton(
    iconRes: Int,
    desc: String,
    shape: Shape,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(shape)
            .background(BackgroundColors.Surface.default.current())
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = desc,
            tint = TextColors.Primary.default.current(),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F2EC, name = "RecycleBin · 顶栏按钮")
@Composable
private fun TopIconButtonPreview() {
    AppTheme {
        TopIconButton(R.drawable.ic_panel_left, "Back", RoundedCornerShape(12.dp))
    }
}
