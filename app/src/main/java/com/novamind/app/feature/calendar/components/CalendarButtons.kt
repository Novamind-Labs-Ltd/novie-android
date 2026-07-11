package com.novamind.app.feature.calendar.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.novamind.app.R
import com.novamind.app.ui.theme.AppTheme

/** 顶部操作胶囊里的圆形图标按钮。 */
@Composable
internal fun PillIcon(iconRes: Int, desc: String, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), desc, tint = ColorTextTitle, modifier = Modifier.size(20.dp))
    }
}

/** 日期导航左右箭头（左箭头由右箭头旋转 180° 得到）。 */
@Composable
internal fun NavArrow(left: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = if (left) "Previous day" else "Next day",
            tint = ColorTextTitle,
            modifier = Modifier
                .size(22.dp)
                .then(if (left) Modifier.rotate(180f) else Modifier),
        )
    }
}

@Preview(showBackground = true, name = "Calendar · Icon button")
@Composable
private fun CalendarButtonsPreview() {
    AppTheme {
        Row {
            NavArrow(left = true) {}
            PillIcon(R.drawable.ic_add, "Add")
            PillIcon(R.drawable.ic_search, "Search")
            NavArrow(left = false) {}
        }
    }
}
