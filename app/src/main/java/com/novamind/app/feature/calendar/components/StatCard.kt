package com.novamind.app.feature.calendar.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.theme.AppTheme

/**
 * 统计卡片（Figma 959-60297）：底色块 + 计数(28sp) + 标签(12sp)，右侧一张略微倾斜的插画。
 * 可点击切换议程过滤，选中时带描边。
 */
@Composable
internal fun StatCard(
    count: String,
    label: String,
    bg: Color,
    illustrationRes: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(if (selected) Modifier.border(2.dp, ColorDark, RoundedCornerShape(8.dp)) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .height(88.dp)
            .clipToBounds(),
    ) {
        // 右侧插画：略微倾斜、部分出血（超出被卡片圆角裁掉）
        Image(
            painter = painterResource(illustrationRes),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 16.dp, y = 6.dp)
                .size(96.dp)
                .rotate(-10.5f),
        )
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(count, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = ColorTextTitle)
            Text(label, fontSize = 12.sp, color = ColorTextTitle)
        }
    }
}

@Preview(showBackground = true, name = "Calendar · Stat card")
@Composable
private fun StatCardPreview() {
    AppTheme {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatCard("0", "Meetings", MeetingBg, R.drawable.illus_stat_meetings, Modifier.weight(1f))
            StatCard("0", "To-dos", TodoBg, R.drawable.illus_stat_todos, Modifier.weight(1f), selected = true)
        }
    }
}
