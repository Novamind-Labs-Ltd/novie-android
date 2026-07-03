package com.novamind.app.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.home.UpcomingItem
import com.novamind.app.ui.theme.AppTheme

/** 首页 Upcoming 卡片：图标 + 标题/副标题。 */
@Composable
internal fun UpcomingCard(
    item: UpcomingItem,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(id = item.iconResId),
                contentDescription = null,
                tint = ColorMenuIcon,
                modifier = Modifier.size(36.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorTextTitle,
                )
                Text(
                    text = item.subtitle,
                    fontSize = 13.sp,
                    color = ColorTextSub,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0EFEA, name = "Home · Upcoming 卡片")
@Composable
private fun UpcomingCardPreview() {
    AppTheme {
        UpcomingCard(
            item = UpcomingItem("1", "Monthly report sharing", "Team project progress tracking", R.drawable.ic_upcoming_report),
        )
    }
}
