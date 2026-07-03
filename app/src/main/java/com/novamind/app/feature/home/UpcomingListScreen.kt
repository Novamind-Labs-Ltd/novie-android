package com.novamind.app.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.home.components.BgCard
import com.novamind.app.feature.home.components.BgPage
import com.novamind.app.feature.home.components.ColorTextTitle
import com.novamind.app.feature.home.components.UpcomingRow
import com.novamind.app.ui.components.BackButton

// 配色与列表行组件在 feature/home/components 包（HomeColors / UpcomingRow）。

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
            BackButton(onClick = onBack, background = BgCard, tint = ColorTextTitle)
            Text("Upcoming", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
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

/** 示例 Upcoming 数据（用于列表页填充） */
val sampleUpcoming: List<UpcomingItem> = listOf(
    UpcomingItem("u1", "Monthly report sharing", "Team project progress tracking", R.drawable.ic_upcoming_report),
    UpcomingItem("u2", "Board meeting", "Internal stakeholder alignment", R.drawable.ic_upcoming_meeting),
    UpcomingItem("u3", "Design review", "Review the new note editor flows", R.drawable.ic_upcoming_report),
    UpcomingItem("u4", "1:1 with Felix", "Hero campaign planning in Hong Kong", R.drawable.ic_upcoming_meeting),
    UpcomingItem("u5", "Quarterly planning", "Finalize budget for product launch", R.drawable.ic_upcoming_report),
    UpcomingItem("u6", "Team offsite", "Mount Serenity logistics & agenda", R.drawable.ic_upcoming_meeting),
)
