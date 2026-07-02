package com.novamind.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.ReadOnlyComposable
import com.novamind.app.R
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

// ─── 导航目标 ────────────────────────────────────────────────────────────────

sealed class BottomNavDestination(
    val route: String,
    val label: String,
    val iconResId: Int?,
) {
    /** 左侧品牌/特殊按钮，无标签 */
    object Brand : BottomNavDestination("brand", "", null)
    object Home : BottomNavDestination("home", "Home", R.drawable.ic_nav_home)
    object Create : BottomNavDestination("create", "Create", R.drawable.ic_nav_create)
    object Library : BottomNavDestination("library", "Library", R.drawable.ic_nav_library)
    object Calendar : BottomNavDestination("calendar", "Calendar", R.drawable.ic_nav_calendar)
}

val bottomNavDestinations = listOf(
    BottomNavDestination.Brand,
    BottomNavDestination.Home,
    BottomNavDestination.Create,
    BottomNavDestination.Library,
    BottomNavDestination.Calendar,
)

// ─── 颜色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析（不使用硬编码颜色） ──

private val ColorSelected: Color
    @Composable @ReadOnlyComposable get() = IconColors.Brand.default.current()
private val ColorUnselected: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.secondary.current()
private val ColorBrand: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.default.current()
private val BarBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()

// ─── 主组件 ───────────────────────────────────────────────────────────────────

/**
 * 底部导航栏
 *
 * @param currentRoute 当前选中路由
 * @param onNavigate   点击回调，携带目标路由字符串
 */
@Composable
fun AppBottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(top = 8.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 左侧：独立圆形品牌胶囊
        Surface(
            color = BarBg,
            shadowElevation = 12.dp,
            shape = RoundedCornerShape(50), // 正圆
        ) {
            BrandNavItem(
                modifier = Modifier.size(58.dp),
                onClick = { onNavigate(BottomNavDestination.Brand.route) },
            )
        }

        // 右侧：四个导航项的长胶囊
        Surface(
            modifier = Modifier.weight(1f),
            color = BarBg,
            shadowElevation = 12.dp,
            shape = RoundedCornerShape(50),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                bottomNavDestinations
                    .filterNot { it is BottomNavDestination.Brand }
                    .forEach { dest ->
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

// ─── 品牌图标按钮 ─────────────────────────────────────────────────────────────

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
                indication = ripple(bounded = false, radius = 28.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_nav_brand),
            contentDescription = "Brand",
            tint = ColorBrand,
            modifier = Modifier.size(22.dp),
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
                indication = ripple(bounded = false, radius = 32.dp),
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = if (selected) {
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(ColorSelected.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            } else {
                Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = destination.iconResId!!),
                contentDescription = destination.label,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.height(1.dp))

        Text(
            text = destination.label,
            fontSize = 10.sp,
            fontWeight = labelWeight,
            color = labelColor,
            maxLines = 1,
        )
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F0)
@Composable
private fun BottomNavBarHomePreview() {
    AppTheme {
        AppBottomNavBar(
            currentRoute = BottomNavDestination.Home.route,
            onNavigate = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F0)
@Composable
private fun BottomNavBarLibraryPreview() {
    AppTheme {
        AppBottomNavBar(
            currentRoute = BottomNavDestination.Library.route,
            onNavigate = {},
        )
    }
}
