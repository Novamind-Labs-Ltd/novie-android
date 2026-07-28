package com.novamind.app.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.theme.AppTheme

/**
 * 分段标签栏：两个等宽 Tab（Recent / Folders），底部一条浅色分隔线，
 * 黑色指示条随 [indicatorFraction] 在两 Tab 间平滑滑动。
 */
@Composable
internal fun SegmentedTabBar(
    selectedIndex: Int,
    indicatorFraction: Float,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val tabWidth = maxWidth / 2
        val indicatorWidth = 76.dp
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                SegmentTab(
                    iconRes = R.drawable.ic_library_tab_recent_unselected,
                    selectedIconRes = R.drawable.ic_library_tab_recent_selected,
                    label = "Recent",
                    selected = selectedIndex == 0,
                    onClick = { onTabClick(0) },
                    modifier = Modifier.weight(1f),
                )
                SegmentTab(
                    iconRes = R.drawable.ic_library_tab_folder_unselected,
                    selectedIconRes = R.drawable.ic_library_tab_folder_selected,
                    label = "Folders",
                    selected = selectedIndex == 1,
                    onClick = { onTabClick(1) },
                    modifier = Modifier.weight(1f),
                )
            }
            // 分隔线 + 滑动指示条叠放
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .align(Alignment.BottomStart)
                        .background(ColorBorder),
                )
                Box(
                    modifier = Modifier
                        .offset(x = tabWidth * indicatorFraction + (tabWidth - indicatorWidth) / 2)
                        .width(indicatorWidth)
                        .height(2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ColorTextTitle),
                )
            }
        }
    }
}

@Composable
private fun SegmentTab(
    iconRes: Int,
    selectedIconRes: Int = iconRes,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(if (selected) selectedIconRes else iconRes),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) ColorTextTitle else ColorTextSub,
        )
    }
}

@Preview(showBackground = true, name = "Library · SegmentedTabBar")
@Composable
private fun SegmentedTabBarPreview() {
    AppTheme {
        SegmentedTabBar(selectedIndex = 0, indicatorFraction = 0f, onTabClick = {})
    }
}

@Preview(showBackground = true, name = "Library · SegmentTab")
@Composable
private fun SegmentTabPreview() {
    AppTheme {
        SegmentTab(
            iconRes = R.drawable.ic_library_tab_recent_unselected,
            selectedIconRes = R.drawable.ic_library_tab_recent_selected,
            label = "Recent",
            selected = true,
            onClick = {},
        )
    }
}
