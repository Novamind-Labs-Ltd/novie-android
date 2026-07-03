package com.novamind.app.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.home.UpcomingItem
import com.novamind.app.ui.theme.AppTheme

/** Upcoming 全屏列表行：圆形图标底 + 标题/副标题。 */
@Composable
internal fun UpcomingRow(item: UpcomingItem) {
    Surface(shape = RoundedCornerShape(14.dp), color = BgCard, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ColorIconCircle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = item.iconResId),
                    contentDescription = null,
                    tint = ColorTextTitle,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ColorTextTitle, maxLines = 1)
                Text(item.subtitle, fontSize = 13.sp, color = ColorTextSub, maxLines = 2)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0EFEA, name = "Home · Upcoming 列表行")
@Composable
private fun UpcomingRowPreview() {
    AppTheme {
        UpcomingRow(
            item = UpcomingItem("1", "Board meeting", "Internal stakeholder alignment", R.drawable.ic_upcoming_meeting),
        )
    }
}
