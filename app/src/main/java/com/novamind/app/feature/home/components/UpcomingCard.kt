package com.novamind.app.feature.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.home.UpcomingItem
import com.novamind.app.ui.theme.AppTheme

/**
 * 首页「Up next」卡片（home_final）：左侧时间戳 + 标题/副标题；首个卡片额外展示深色动作按钮。
 * 圆角 12dp、白底、轻投影，与设计稿一致；无图标（旧图标版由 UpcomingRow 承载）。
 */
@Composable
internal fun UpcomingCard(
    item: UpcomingItem,
    modifier: Modifier = Modifier,
    showAction: Boolean = false,
    actionLabel: String = "Start notes",
    onAction: () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(12.dp),
        color = BgCard,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (item.time.isNotBlank()) {
                    Text(
                        text = item.time,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ColorTimeStamp,
                        modifier = Modifier.width(34.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ColorTextTitle,
                    )
                    Text(
                        text = item.subtitle,
                        fontSize = 14.sp,
                        color = ColorTextSub,
                    )
                }
            }

            if (showAction) {
                Surface(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    shape = RoundedCornerShape(100.dp),
                    color = ColorButtonDark,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = actionLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = ColorOnDark,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Home · Up next Card (with action)")
@Composable
private fun UpcomingCardActionPreview() {
    AppTheme {
        UpcomingCard(
            item = UpcomingItem("1", "Monthly report sharing", "Team project progress tracking", R.drawable.ic_upcoming_report, time = "10:00"),
            showAction = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Home · Up next Card")
@Composable
private fun UpcomingCardPreview() {
    AppTheme {
        UpcomingCard(
            item = UpcomingItem("2", "Board meeting", "Internal stakeholder alignment", R.drawable.ic_upcoming_meeting, time = "11:30"),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A, name = "Home · Up next Card (Dark)")
@Composable
private fun UpcomingCardDarkPreview() {
    AppTheme(darkTheme = true) {
        UpcomingCard(
            item = UpcomingItem("1", "Monthly report sharing", "Team project progress tracking", R.drawable.ic_upcoming_report, time = "10:00"),
            showAction = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}
