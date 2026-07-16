package com.novamind.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.Palette
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

// ─── 导航目标 ────────────────────────────────────────────────────────────────

sealed class BottomNavDestination(
    val route: String,
    val label: String,
    val iconResId: Int?,
) {
    object Home : BottomNavDestination("home", "Home", R.drawable.ic_nav_home)
    object Calendar : BottomNavDestination("calendar", "Calendar", R.drawable.ic_nav_calendar)
    /** 中央 FAB 对应的编辑页路由（不作为可见 tab 渲染）。 */
    object Create : BottomNavDestination("create", "Create", R.drawable.ic_nav_create)
    object Library : BottomNavDestination("library", "Library", R.drawable.ic_nav_library)
    object Profile : BottomNavDestination("profile", "Profile", R.drawable.ic_nav_profile)
}

/** 底栏可见 tab（左二 / 右二，中间留出 FAB 凹槽）。 */
val bottomNavTabs = listOf(
    BottomNavDestination.Home,
    BottomNavDestination.Calendar,
    BottomNavDestination.Library,
    BottomNavDestination.Profile,
)

// ─── 颜色：统一引用 ui/colors 设计令牌，随主题深浅自动解析 ────────────────────────

private val ColorSelected: Color
    @Composable @ReadOnlyComposable get() = IconColors.Success.default.current()   // #145436
private val ColorUnselected: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.default.current()    // #333
private val BarBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val FabBorder: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.default.current()

// ─── 尺寸 ─────────────────────────────────────────────────────────────────────

private val BarHeight = 68.dp
private val FabSize = 60.dp
private val SubButtonSize = 48.dp
private val CradleWidth = 96.dp
private val CradleDepth = 30.dp
private val TopCorner = 22.dp

// ─── 中央带凹槽的顶边形状 ─────────────────────────────────────────────────────

/** 顶边中央下凹的栏体形状（平滑三次贝塞尔谷），用于容纳浮动 FAB。 */
private class CradleTopShape(
    private val cradleWidth: Dp,
    private val cradleDepth: Dp,
    private val topCorner: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        with(density) {
            val w = size.width
            val h = size.height
            val half = cradleWidth.toPx() / 2f
            val depth = cradleDepth.toPx()
            val corner = topCorner.toPx()
            val cx = w / 2f

            path.moveTo(0f, corner)
            path.quadraticBezierTo(0f, 0f, corner, 0f)               // 左上圆角
            path.lineTo(cx - half, 0f)                                // 平直到凹槽起点
            path.cubicTo(cx - half * 0.5f, 0f, cx - half * 0.5f, depth, cx, depth)   // 谷左半
            path.cubicTo(cx + half * 0.5f, depth, cx + half * 0.5f, 0f, cx + half, 0f) // 谷右半
            path.lineTo(w - corner, 0f)
            path.quadraticBezierTo(w, 0f, w, corner)                  // 右上圆角
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()
        }
        return Outline.Generic(path)
    }
}

// ─── 主组件 ───────────────────────────────────────────────────────────────────

/**
 * 底部导航栏（home_final / nav）：中央下凹栏体 + 浮动「+」FAB，两侧各两个 tab。
 * 「+」点击展开 Ask Novie / Create 两个速拨子按钮。
 *
 * @param currentRoute 当前选中路由
 * @param onNavigate   tab 点击回调（Home/Calendar/Library/Profile）
 * @param onCreate     速拨 Create（新建笔记）
 * @param onAskNovie   速拨 Ask Novie
 */
@Composable
fun AppBottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onCreate: () -> Unit = {},
    onAskNovie: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val fabRotation by animateFloatAsState(if (expanded) 45f else 0f, label = "fab_rotation")
    val cradle = remember { CradleTopShape(CradleWidth, CradleDepth, TopCorner) }

    // 预留 FAB 上探与速拨区域的高度
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(BarHeight + 120.dp),
    ) {
        // ── 栏体（含四个 tab） ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(BarHeight)
                .shadow(elevation = 10.dp, shape = cradle, clip = false)
                .background(BarBg, cradle),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavTab(BottomNavDestination.Home, currentRoute, Modifier.weight(1f)) { expanded = false; onNavigate(it) }
                NavTab(BottomNavDestination.Calendar, currentRoute, Modifier.weight(1f)) { expanded = false; onNavigate(it) }
                Spacer(Modifier.width(CradleWidth))   // 中间凹槽让位给 FAB
                NavTab(BottomNavDestination.Library, currentRoute, Modifier.weight(1f)) { expanded = false; onNavigate(it) }
                NavTab(BottomNavDestination.Profile, currentRoute, Modifier.weight(1f)) { expanded = false; onNavigate(it) }
            }
        }

        // ── 速拨子按钮（Ask / Create），展开时浮于 FAB 之上 ─────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -(BarHeight - FabSize / 2 + FabSize + 16.dp)),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                SpeedDialButton(R.drawable.ic_chat, "Ask Novie") { expanded = false; onAskNovie() }
                SpeedDialButton(R.drawable.ic_nav_create, "Create") { expanded = false; onCreate() }
            }
        }

        // ── 中央「+」FAB（点击切换速拨） ────────────────────────────────────
        Surface(
            shape = CircleShape,
            color = BarBg,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, FabBorder),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -(BarHeight - FabSize / 2))
                .size(FabSize),
        ) {
            Box(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = FabSize / 2),
                    onClick = { expanded = !expanded },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add),
                    contentDescription = if (expanded) "Close" else "Create",
                    tint = ColorUnselected,
                    modifier = Modifier.size(28.dp).rotate(fabRotation),
                )
            }
        }
    }
}

// ─── 速拨子按钮 ───────────────────────────────────────────────────────────────

@Composable
private fun SpeedDialButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = BarBg,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, Palette.gray600),
        modifier = Modifier.size(SubButtonSize),
    ) {
        Box(
            modifier = Modifier.clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                tint = ColorUnselected,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

// ─── 普通 tab ─────────────────────────────────────────────────────────────────

@Composable
private fun NavTab(
    destination: BottomNavDestination,
    currentRoute: String,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit,
) {
    val selected = currentRoute == destination.route
    val tint = if (selected) ColorSelected else ColorUnselected
    val weight = if (selected) FontWeight.SemiBold else FontWeight.Medium

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 32.dp),
                onClick = { onClick(destination.route) },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(id = destination.iconResId!!),
            contentDescription = destination.label,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = destination.label,
            fontSize = 12.sp,
            fontWeight = weight,
            color = tint,
            maxLines = 1,
        )
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Bottom Nav · Home")
@Composable
private fun BottomNavBarHomePreview() {
    AppTheme {
        AppBottomNavBar(currentRoute = BottomNavDestination.Home.route, onNavigate = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Bottom Nav · Library")
@Composable
private fun BottomNavBarLibraryPreview() {
    AppTheme {
        AppBottomNavBar(currentRoute = BottomNavDestination.Library.route, onNavigate = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A, name = "Bottom Nav · Dark")
@Composable
private fun BottomNavBarDarkPreview() {
    AppTheme(darkTheme = true) {
        AppBottomNavBar(currentRoute = BottomNavDestination.Profile.route, onNavigate = {})
    }
}
