package com.novamind.app.ui.components

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.draw.rotate
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
) {
    object Home : BottomNavDestination("home", "Home")
    object Calendar : BottomNavDestination("calendar", "Calendar")
    /** 中央 FAB 对应的编辑页路由（不作为可见 tab 渲染）。 */
    object Create : BottomNavDestination("create", "Create")
    object Library : BottomNavDestination("library", "Library")
    object Profile : BottomNavDestination("profile", "Profile")
}

/**
 * 目标对应的图标资源。注意：故意在渲染期用 `when` 解析，而不是把 `R.drawable.*` 存进
 * object 构造里——后者会在类初始化（<clinit>）期引用资源 ID，在 Compose 预览(layoutlib)
 * 下易触发「Could not initialize class」而导致预览崩溃。
 */
@DrawableRes
private fun BottomNavDestination.iconRes(): Int = when (this) {
    BottomNavDestination.Home -> R.drawable.ic_nav_home
    BottomNavDestination.Calendar -> R.drawable.ic_nav_calendar
    BottomNavDestination.Create -> R.drawable.ic_nav_create
    BottomNavDestination.Library -> R.drawable.ic_nav_library
    BottomNavDestination.Profile -> R.drawable.ic_nav_profile
}

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
/** FAB 上探（一半探出栏体上沿）与速拨预留区，用作整体高度与各元素纵向偏移。 */
private val FabTopOffset = BarHeight - FabSize / 2            // FAB 相对底部上移量
private val SpeedDialTopOffset = FabTopOffset + FabSize + 16.dp // 速拨相对底部上移量
private val NavBarHeight = BarHeight + 120.dp                 // 预留 FAB/速拨的整体高度

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(NavBarHeight),
    ) {
        // 栏体 + 四个 tab（点 tab 顺便收起速拨）
        CradleBar(
            currentRoute = currentRoute,
            modifier = Modifier.align(Alignment.BottomCenter),
            onNavigate = { expanded = false; onNavigate(it) },
        )

        // 速拨子按钮（Ask / Create），展开时浮于 FAB 之上
        SpeedDialRow(
            visible = expanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -SpeedDialTopOffset),
            onAskNovie = { expanded = false; onAskNovie() },
            onCreate = { expanded = false; onCreate() },
        )

        // 中央「+」FAB（点击切换速拨）
        CenterFab(
            expanded = expanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -FabTopOffset),
            onClick = { expanded = !expanded },
        )
    }
}

// ─── 栏体（中央下凹 + 四个 tab） ───────────────────────────────────────────────

@Composable
private fun CradleBar(
    currentRoute: String,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit,
) {
    val cradle = remember { CradleTopShape(CradleWidth, CradleDepth, TopCorner) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .background(BarBg, cradle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab(BottomNavDestination.Home, currentRoute, Modifier.weight(1f), onNavigate)
            NavTab(BottomNavDestination.Calendar, currentRoute, Modifier.weight(1f), onNavigate)
            Spacer(Modifier.width(CradleWidth))   // 中间凹槽让位给 FAB
            NavTab(BottomNavDestination.Library, currentRoute, Modifier.weight(1f), onNavigate)
            NavTab(BottomNavDestination.Profile, currentRoute, Modifier.weight(1f), onNavigate)
        }
    }
}

// ─── 中央「+」FAB ─────────────────────────────────────────────────────────────

@Composable
private fun CenterFab(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 45f else 0f, label = "fab_rotation")
    Surface(
        shape = CircleShape,
        color = BarBg,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, FabBorder),
        modifier = modifier.size(FabSize),
    ) {
        Box(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = FabSize / 2),
                onClick = onClick,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_add),
                contentDescription = if (expanded) "Close" else "Create",
                tint = ColorUnselected,
                modifier = Modifier
                    .size(28.dp)
                    .rotate(rotation),
            )
        }
    }
}

// ─── 速拨子按钮 ───────────────────────────────────────────────────────────────

@Composable
private fun SpeedDialRow(
    visible: Boolean,
    modifier: Modifier = Modifier,
    onAskNovie: () -> Unit,
    onCreate: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.6f),
        exit = fadeOut() + scaleOut(targetScale = 0.6f),
        modifier = modifier,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            SpeedDialButton(R.drawable.ic_chat, "Ask Novie", onAskNovie)
            SpeedDialButton(R.drawable.ic_nav_create, "Create", onCreate)
        }
    }
}

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
            painter = painterResource(id = destination.iconRes()),
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

// ─── 中央下凹的顶边形状 ───────────────────────────────────────────────────────

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
            path.quadraticBezierTo(0f, 0f, corner, 0f)                                  // 左上圆角
            path.lineTo(cx - half, 0f)                                                   // 平直到凹槽起点
            path.cubicTo(cx - half * 0.5f, 0f, cx - half * 0.5f, depth, cx, depth)       // 谷左半
            path.cubicTo(cx + half * 0.5f, depth, cx + half * 0.5f, 0f, cx + half, 0f)   // 谷右半
            path.lineTo(w - corner, 0f)
            path.quadraticBezierTo(w, 0f, w, corner)                                     // 右上圆角
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()
        }
        return Outline.Generic(path)
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
