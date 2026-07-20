package com.novamind.app.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.novamind.app.R
import com.novamind.app.feature.library.LibraryViewMode
import com.novamind.app.ui.theme.AppTheme

/**
 * 视图切换按钮：圆角方形白底按钮（区别于顶部圆形搜索按钮），
 * 图标显示「当前」视图模式——网格态显示网格图标、列表态显示列表图标，点击切换。
 */
@Composable
internal fun ViewModeToggle(viewMode: LibraryViewMode, onClick: () -> Unit, visible: Boolean = true) {
    val isGrid = viewMode == LibraryViewMode.GRID
    Box(
        modifier = Modifier
            .size(42.dp)
            // 不可见时仍保留占位（alpha 0），并禁用点击
            .alpha(if (visible) 1f else 0f)
            .clip(RoundedCornerShape(12.dp))
            .background(ColorIconBtn)
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
            .clickable(enabled = visible, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            // 显示「将切换到的」目标视图图标（对齐 Figma：网格态显示列表图标，列表态显示网格图标）
            painter = painterResource(if (isGrid) R.drawable.ic_format_list else R.drawable.ic_grid),
            contentDescription = if (isGrid) "Switch to list view" else "Switch to grid view",
            tint = ColorTextTitle,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Preview(showBackground = true, name = "Library · ViewModeToggle Grid")
@Composable
private fun ViewModeTogglePreview() {
    AppTheme {
        ViewModeToggle(viewMode = LibraryViewMode.GRID, onClick = {})
    }
}

@Preview(showBackground = true, name = "Library · ViewModeToggle List")
@Composable
private fun ViewModeToggleListPreview() {
    AppTheme {
        ViewModeToggle(viewMode = LibraryViewMode.LIST, onClick = {})
    }
}
