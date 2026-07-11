package com.novamind.app.feature.create.tag.tagmanager.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.novamind.app.R
import com.novamind.app.common.config.AppConfig
import com.novamind.app.ui.theme.AppTheme

/** 标签颜色圆点：淡色底 + 标签图标，选中加粗描边。CreateTagSheet 与改色弹层共用。 */
@Composable
internal fun TagColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) ColorTextTitle else ColorBorder,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tag),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0EFEA, name = "Tag · Color Swatch")
@Composable
private fun TagColorSwatchPreview() {
    AppTheme {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppConfig.Folder.COLORS.take(4).forEachIndexed { i, c ->
                TagColorSwatch(color = c, selected = i == 0) {}
            }
        }
    }
}
