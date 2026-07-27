package com.novamind.app.feature.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novamind.app.R
import com.novamind.app.ui.theme.AppTheme

@Composable
internal fun TopIconButton(
    iconRes: Int,
    desc: String,
    shape: androidx.compose.ui.graphics.Shape,
    bg: Color = ColorIconBtn,
    buttonSize: Dp = 42.dp,
    iconWidth: Dp = 20.dp,
    iconHeight: Dp = 20.dp,
    border: BorderStroke? = null,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(buttonSize)
            .clip(shape)
            .background(bg)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = desc,
            tint = ColorTextTitle,
            modifier = Modifier.size(width = iconWidth, height = iconHeight),
        )
    }
}

@Preview(showBackground = true, name = "Library · TopIconButton")
@Composable
private fun TopIconButtonPreview() {
    AppTheme {
        TopIconButton(iconRes = R.drawable.ic_search, desc = "Search", shape = CircleShape)
    }
}
