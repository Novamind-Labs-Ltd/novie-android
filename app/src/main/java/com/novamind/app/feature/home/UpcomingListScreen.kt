package com.novamind.app.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R

private val BgPage = Color(0xFFF0EFEA)
private val Card = Color(0xFFFFFFFF)
private val TextTitle = Color(0xFF1A1A1A)
private val TextSub = Color(0xFF6B6B6B)

/** Upcoming 列表全屏页（示例填充）。 */
@Composable
fun UpcomingListScreen(
    items: List<UpcomingItem>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = Card, shadowElevation = 2.dp) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false),
                            onClick = onBack,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint = TextTitle,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text("Upcoming", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextTitle)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { item -> UpcomingRow(item) }
        }
    }
}

@Composable
private fun UpcomingRow(item: UpcomingItem) {
    Surface(shape = RoundedCornerShape(14.dp), color = Card, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEDEAE2)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = item.iconResId),
                    contentDescription = null,
                    tint = TextTitle,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextTitle, maxLines = 1)
                Text(item.subtitle, fontSize = 13.sp, color = TextSub, maxLines = 2)
            }
        }
    }
}

/** 示例 Upcoming 数据（用于列表页填充） */
val sampleUpcoming: List<UpcomingItem> = listOf(
    UpcomingItem("u1", "Monthly report sharing", "Team project progress tracking", R.drawable.ic_upcoming_report),
    UpcomingItem("u2", "Board meeting", "Internal stakeholder alignment", R.drawable.ic_upcoming_meeting),
    UpcomingItem("u3", "Design review", "Review the new note editor flows", R.drawable.ic_upcoming_report),
    UpcomingItem("u4", "1:1 with Felix", "Hero campaign planning in Hong Kong", R.drawable.ic_upcoming_meeting),
    UpcomingItem("u5", "Quarterly planning", "Finalize budget for product launch", R.drawable.ic_upcoming_report),
    UpcomingItem("u6", "Team offsite", "Mount Serenity logistics & agenda", R.drawable.ic_upcoming_meeting),
)
