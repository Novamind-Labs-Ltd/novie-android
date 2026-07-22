package com.novamind.app.feature.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.novamind.app.feature.library.LibraryViewMode
import com.novamind.app.ui.components.shimmer
import com.novamind.app.ui.theme.AppTheme

// 骨架卡片底色复用组件内既有令牌 [BgCard]（Surface.default），与真实 LibraryNoteCard 对齐

/**
 * Recent 页首屏加载骨架：数据到位前先占位，避免空态一闪。布局尽量贴合真实内容
 * （网格瀑布流 / 单列列表两态），占位块用 [shimmer] 扫光。骨架本身不可滚动。
 */
@Composable
internal fun LibraryRecentSkeleton(viewMode: LibraryViewMode, modifier: Modifier = Modifier) {
    if (viewMode == LibraryViewMode.GRID) {
        // 双列瀑布流：两列错开高度，模拟真实卡片的参差
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(PaddingValues(top = 16.dp)),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SkeletonColumn(Modifier.weight(1f), withImage = listOf(true, false, true))
            SkeletonColumn(Modifier.weight(1f), withImage = listOf(false, true, false))
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(PaddingValues(top = 16.dp)),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(6) { SkeletonCard(withImage = false) }
        }
    }
}

/** 单列骨架卡片序列（供网格两列复用），列内竖向间距对齐真实网格的 verticalItemSpacing。 */
@Composable
private fun SkeletonColumn(modifier: Modifier, withImage: List<Boolean>) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        withImage.forEach { SkeletonCard(withImage = it) }
    }
}

/** 单张骨架卡片：形状/内边距/间距与 [com.novamind.app.feature.library.LibraryNoteCard] 保持一致。 */
@Composable
private fun SkeletonCard(withImage: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = BgCard,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (withImage) Bar(Modifier.fillMaxWidth().height(120.dp), corner = 8.dp)
            // 标题 + 两行摘要
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Bar(Modifier.fillMaxWidth(0.6f).height(14.dp))
                Bar(Modifier.fillMaxWidth().height(12.dp))
                Bar(Modifier.fillMaxWidth(0.8f).height(12.dp))
            }
            // 日期
            Bar(Modifier.fillMaxWidth(0.35f).height(10.dp))
        }
    }
}

/** 圆角扫光占位条。 */
@Composable
private fun Bar(modifier: Modifier, corner: androidx.compose.ui.unit.Dp = 6.dp) {
    Box(modifier.clip(RoundedCornerShape(corner)).shimmer())
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F2EC, name = "Library · RecentSkeleton Grid")
@Composable
private fun LibraryRecentSkeletonGridPreview() {
    AppTheme { LibraryRecentSkeleton(viewMode = LibraryViewMode.GRID) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F2EC, name = "Library · RecentSkeleton List")
@Composable
private fun LibraryRecentSkeletonListPreview() {
    AppTheme { LibraryRecentSkeleton(viewMode = LibraryViewMode.LIST) }
}
