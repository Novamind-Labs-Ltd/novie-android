package com.novamind.app.feature.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.theme.AppTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val BgPage = Color(0xFFF4F2EC)
private val ColorTextTitle = Color(0xFF1A1A1A)
private val ColorTextSub = Color(0xFF6B6B6B)
private val ColorTextFaint = Color(0xFFB4B0A6)
private val ColorBorder = Color(0xFFE3E0D8)
private val ColorPrimary = Color(0xFF3D7A5A)
private val MeetingBg = Color(0xFFDCEAF1)
private val MeetingIcon = Color(0xFF5B89A6)
private val TodoBg = Color(0xFFE9E7E1)
private val TodoIcon = Color(0xFF2E7D6B)

private val weekLetters = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun CalendarScreen(
    onConnect: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var morningOpen by remember { mutableStateOf(false) }
    var afternoonOpen by remember { mutableStateOf(false) }

    val monday = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week = remember(monday) { (0..6).map { monday.plusDays(it.toLong()) } }
    val dayName = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val dateStr = selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 100.dp),
    ) {
        // 顶部标题 + 操作 pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Calendar", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = ColorTextTitle)
            Surface(shape = RoundedCornerShape(50), color = Color.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillIcon(R.drawable.ic_add, "Add")
                    PillIcon(R.drawable.ic_search, "Search")
                    PillIcon(R.drawable.ic_more, "More")
                }
            }
        }

        // 日期导航：‹ 周四 / 18 June 2026 ›
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavArrow(left = true) { selectedDate = selectedDate.minusDays(1) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dayName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
                Text(dateStr, fontSize = 13.sp, color = ColorTextSub)
            }
            NavArrow(left = false) { selectedDate = selectedDate.plusDays(1) }
        }

        Spacer(Modifier.height(12.dp))

        // 周条
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
            week.forEachIndexed { i, date ->
                DayCell(
                    letter = weekLetters[i],
                    day = date.dayOfMonth,
                    selected = date == selectedDate,
                    weekend = i >= 5,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedDate = date },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 统计卡片
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard("0", "Meetings", MeetingBg, R.drawable.ic_nav_calendar, MeetingIcon, Modifier.weight(1f))
            StatCard("0", "To-dos", TodoBg, R.drawable.ic_check_circle, TodoIcon, Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))

        // 空状态：连接 Google 日历
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CalendarIllustration()
            Spacer(Modifier.height(20.dp))
            Text("Connect to Google Calendar", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
            Spacer(Modifier.height(8.dp))
            Text(
                "Link your account to sync your events and keep your calendar up-to-date.",
                fontSize = 14.sp,
                color = ColorTextSub,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF111111))
                    .clickable(onClick = onConnect)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_nav_calendar), null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("Connect to google calendar", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // 时段分组
        SectionRow(R.drawable.ic_morning, "Morning", 0, morningOpen) { morningOpen = !morningOpen }
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(vertical = 4.dp).height(1.dp).background(ColorBorder))
        SectionRow(R.drawable.ic_sun, "Afternoon", 0, afternoonOpen) { afternoonOpen = !afternoonOpen }
    }
}

@Composable
private fun PillIcon(iconRes: Int, desc: String, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), desc, tint = ColorTextTitle, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun NavArrow(left: Boolean, onClick: () -> Unit) {
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

@Composable
private fun DayCell(
    letter: String,
    day: Int,
    selected: Boolean,
    weekend: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(letter, fontSize = 12.sp, color = if (weekend) ColorTextFaint else ColorTextSub)
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .then(if (selected) Modifier.background(ColorPrimary) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.toString(),
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else ColorTextTitle,
            )
        }
    }
}

@Composable
private fun StatCard(
    count: String,
    label: String,
    bg: Color,
    iconRes: Int,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .height(92.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconTint.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(48.dp),
        )
        Column(modifier = Modifier.padding(16.dp)) {
            Text(count, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
            Text(label, fontSize = 13.sp, color = ColorTextSub)
        }
    }
}

@Composable
private fun SectionRow(iconRes: Int, label: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, ColorBorder, RoundedCornerShape(50))
                .clickable(onClick = onToggle),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(iconRes), null, tint = ColorTextTitle, modifier = Modifier.size(16.dp))
                Text("$label ($count)", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ColorTextTitle)
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_down),
                    contentDescription = null,
                    tint = ColorTextSub,
                    modifier = Modifier.size(16.dp).rotate(if (expanded) 180f else 0f),
                )
            }
        }
        if (expanded) {
            Text(
                "No events",
                fontSize = 13.sp,
                color = ColorTextFaint,
                modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 4.dp),
            )
        }
    }
}

/** 空状态插图：叠放的笔记本/文件夹 + 装饰圆点（纯 Canvas，无图片资源）。 */
@Composable
private fun CalendarIllustration() {
    Canvas(modifier = Modifier.size(width = 168.dp, height = 124.dp)) {
        val w = size.width
        val h = size.height
        drawOval(Color(0x12000000), topLeft = Offset(w * 0.20f, h * 0.84f), size = Size(w * 0.60f, h * 0.12f))
        // 后封面
        drawRoundRect(Color(0xFFE7E3D8), topLeft = Offset(w * 0.26f, h * 0.18f), size = Size(w * 0.46f, h * 0.56f), cornerRadius = CornerRadius(10f, 10f))
        // 白页
        drawRoundRect(Color.White, topLeft = Offset(w * 0.31f, h * 0.24f), size = Size(w * 0.40f, h * 0.52f), cornerRadius = CornerRadius(8f, 8f))
        // 绿色书签/卡
        drawRoundRect(ColorPrimary, topLeft = Offset(w * 0.30f, h * 0.46f), size = Size(w * 0.14f, h * 0.14f), cornerRadius = CornerRadius(4f, 4f))
        drawRoundRect(MeetingIcon, topLeft = Offset(w * 0.50f, h * 0.58f), size = Size(w * 0.12f, h * 0.12f), cornerRadius = CornerRadius(4f, 4f))
        // 装饰
        drawCircle(Color(0xFFCDC8BC), radius = w * 0.05f, center = Offset(w * 0.80f, h * 0.30f))
        drawCircle(Color(0xFFE2DED4), radius = w * 0.055f, center = Offset(w * 0.18f, h * 0.66f))
        drawCircle(ColorPrimary.copy(alpha = 0.4f), radius = w * 0.016f, center = Offset(w * 0.74f, h * 0.7f))
    }
}

@Composable
fun CalendarRoute(
    onConnect: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    CalendarScreen(onConnect = onConnect, modifier = modifier)
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CalendarScreenPreview() {
    AppTheme { CalendarScreen() }
}
