package com.example.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── 导航目标 ────────────────────────────────────────────────────────────────

sealed class BottomNavDestination(
    val route: String,
    val label: String,
    /** null 表示使用特殊图标槽（例如品牌 Logo 按钮） */
    val iconResId: Int?,
) {
    /** 左侧品牌/特殊按钮，无标签 */
    object Brand : BottomNavDestination("brand", "", null)

    object Home : BottomNavDestination("home", "Home", R.drawable.ic_nav_home)
    object Create : BottomNavDestination("create", "Create", R.drawable.ic_nav_create)
    object Library : BottomNavDestination("library", "Library", R.drawable.ic_nav_library)
    object Calendar : BottomNavDestination("calendar", "Calendar", R.drawable.ic_nav_calendar)
}

private val destinations = listOf(
    BottomNavDestination.Brand,
    BottomNavDestination.Home,
    BottomNavDestination.Create,
    BottomNavDestination.Library,
    BottomNavDestination.Calendar,
)

// ─── 颜色 token ───────────────────────────────────────────────────────────────

private val ColorSelected = Color(0xFF3D7A5A)   // 绿色（选中态）
private val ColorUnselected = Color(0xFF8A8A8A) // 灰色（未选中）
private val ColorBrand = Color(0xFF1A1A1A)      // 品牌图标深色
private val ColorBarBackground = Color(0xFFFFFFFF)
private val ColorBarShadow = Color(0x1A000000)  // 轻微投影

// ─── 主组件 ───────────────────────────────────────────────────────────────────

/**
 * 底部导航栏
 *
 * @param currentRoute  当前选中路由，与 [BottomNavDestination.route] 匹配
 * @param onNavigate    点击回调，携带目标路由字符串
 * @param modifier      外部修饰符
 */
@Composable
fun AppBottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ColorBarBackground,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(72.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { dest ->
                if (dest is BottomNavDestination.Brand) {
                    BrandNavItem(
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(dest.route) },
                    )
                } else {
                    RegularNavItem(
                        destination = dest,
                        selected = currentRoute == dest.route,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(dest.route) },
                    )
                }
            }
        }
    }
}

// ─── 品牌/特殊图标按钮 ─────────────────────────────────────────────────────────

@Composable
private fun BrandNavItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = false, radius = 28.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 使用 ic_nav_brand 作为品牌图标（花/雪花形），尺寸略大于普通 icon
        Icon(
            painter = painterResource(id = R.drawable.ic_nav_brand),
            contentDescription = "Brand",
            tint = ColorBrand,
            modifier = Modifier.size(30.dp),
        )
    }
}

// ─── 普通导航项 ───────────────────────────────────────────────────────────────

@Composable
private fun RegularNavItem(
    destination: BottomNavDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconTint = if (selected) ColorSelected else ColorUnselected
    val labelColor = if (selected) ColorSelected else ColorUnselected
    val labelWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = false, radius = 32.dp),
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 选中时用圆角背景高亮图标区域
        Box(
            modifier = if (selected) {
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(ColorSelected.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            } else {
                Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = destination.iconResId!!),
                contentDescription = destination.label,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = destination.label,
            fontSize = 11.sp,
            fontWeight = labelWeight,
            color = labelColor,
            maxLines = 1,
        )
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F0)
@Composable
private fun BottomNavBarHomeSelectedPreview() {
    AppBottomNavBar(
        currentRoute = BottomNavDestination.Home.route,
        onNavigate = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F0)
@Composable
private fun BottomNavBarLibrarySelectedPreview() {
    AppBottomNavBar(
        currentRoute = BottomNavDestination.Library.route,
        onNavigate = {},
    )
}
