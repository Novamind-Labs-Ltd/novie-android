package com.novamind.app.feature.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.novamind.app.R
import com.novamind.app.feature.library.LibraryViewMode
import com.novamind.app.ui.theme.AppTheme

/**
 * 视图切换按钮：40dp 无底色点击区域、8dp 圆角，
 * 图标显示点击后将切换到的目标视图模式。
 */
@Composable
internal fun ViewModeToggle(viewMode: LibraryViewMode, onClick: () -> Unit, visible: Boolean = true) {
    val isGrid = viewMode == LibraryViewMode.GRID
    Box(
        modifier = Modifier
            .size(40.dp)
            // 不可见时仍保留占位（alpha 0），并禁用点击
            .alpha(if (visible) 1f else 0f)
            .clip(RoundedCornerShape(8.dp))
            .semantics {
                contentDescription = if (isGrid) "Switch to list view" else "Switch to grid view"
            }
            .clickable(enabled = visible, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 显示「将切换到的」目标视图图标（对齐 Figma：网格态显示列表图标，列表态显示网格图标）
        if (isGrid) {
            Icon(
                painter = painterResource(R.drawable.ic_library_view_list_vector),
                contentDescription = null,
                tint = ColorIconDefault,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Column(
                modifier = Modifier.size(18.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .border(1.5.dp, ColorIconDefault, RoundedCornerShape(1.dp)),
                            )
                        }
                    }
                }
            }
        }
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
