package com.novamind.app.feature.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.feature.home.HomeMenuItem
import com.novamind.app.feature.home.HomeMenuSection
import com.novamind.app.ui.theme.AppTheme

/** 首页「更多」底部弹窗菜单（Settings / Support / About 分组）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoreSheet(
    onDismiss: () -> Unit,
    onItemClick: (HomeMenuItem) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // 直接展开到内容高度，不需要用户上拉（跳过半展开态）
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BgCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        MoreSheetContent(onItemClick = onItemClick)
    }
}

/** 「更多」菜单内容（与 ModalBottomSheet 解耦，便于 @Preview 直接预览）。 */
@Composable
private fun MoreSheetContent(onItemClick: (HomeMenuItem) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
    ) {
        HomeMenuSection.entries.forEach { section ->
            Text(
                text = section.title,
                fontSize = 12.sp,
                color = ColorTextHint,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
            )
            HomeMenuItem.entries.filter { it.section == section }.forEach { item ->
                MoreSheetRow(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun MoreSheetRow(item: HomeMenuItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(id = item.iconRes),
            contentDescription = null,
            tint = ColorTextTitle,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = item.label,
            fontSize = 16.sp,
            color = ColorTextTitle,
        )
    }
}

// ModalBottomSheet 为窗口层，静态预览不渲染；预览内容层。
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "Home · More 菜单内容")
@Composable
private fun MoreSheetContentPreview() {
    AppTheme {
        MoreSheetContent(onItemClick = {})
    }
}
