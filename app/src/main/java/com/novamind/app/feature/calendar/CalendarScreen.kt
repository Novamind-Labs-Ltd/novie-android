package com.novamind.app.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.theme.AppTheme

private val BgPage = Color(0xFFF0EFEA)
private val ColorTextTitle = Color(0xFF1A1A1A)
private val ColorTextSub = Color(0xFF6B6B6B)
private val ColorPrimary = Color(0xFF3D7A5A)

private val weekDays = listOf("M", "T", "W", "T", "F", "S", "S")
private val calendarDays = (1..30).toList()

private data class CalendarEvent(val time: String, val title: String, val color: Color)

private val sampleEvents = listOf(
    CalendarEvent("09:00 AM", "Monthly report sharing", Color(0xFF3D7A5A)),
    CalendarEvent("11:30 AM", "Board meeting", Color(0xFF7A6D3D)),
    CalendarEvent("02:00 PM", "Design review", Color(0xFF3D5A7A)),
)

@Composable
fun CalendarScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding()
            .padding(bottom = 100.dp),
    ) {
        Text(
            text = "Calendar",
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ColorTextTitle,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        )

        // 月份标题
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "June 2026",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextTitle,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                // 星期行
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekDays.forEach { day ->
                        Text(
                            text = day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = ColorTextSub,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 日期格子（简化布局，从周二开始）
                val offset = 1 // June 2026 starts on Monday
                val totalCells = calendarDays.size + offset
                val rows = (totalCells + 6) / 7

                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (col in 0 until 7) {
                            val index = row * 7 + col - offset
                            val day = if (index in calendarDays.indices) calendarDays[index] else null
                            val isToday = day == 9

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .then(
                                        if (isToday) Modifier.background(ColorPrimary, CircleShape)
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (day != null) {
                                    Text(
                                        text = day.toString(),
                                        fontSize = 13.sp,
                                        color = if (isToday) Color.White else ColorTextTitle,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 今日事件
        Text(
            text = "Today",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = ColorTextTitle,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            sampleEvents.forEach { event ->
                EventCard(event = event)
            }
        }
    }
}

@Composable
private fun EventCard(event: CalendarEvent) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .background(event.color, RoundedCornerShape(2.dp))
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(event.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ColorTextTitle)
                Text(event.time, fontSize = 12.sp, color = ColorTextSub)
            }
        }
    }
}

@Composable
fun CalendarRoute(modifier: Modifier = Modifier) {
    CalendarScreen(modifier = modifier)
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CalendarScreenPreview() {
    AppTheme { CalendarScreen() }
}
