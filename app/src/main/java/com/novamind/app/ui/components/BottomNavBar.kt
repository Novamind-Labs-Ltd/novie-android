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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import com.novamind.app.ui.colors.BorderColors
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

/**
 * 选中态专属图标（设计 selected 变体：填充绿 #145436 + 白色内容）。为双色素材，
 * 渲染时不 tint（tint = Color.Unspecified），保留自带配色；返回 null 表示该 tab 无
 * 专属选中图（如 Home 沿用实心图标，靠 tint 变绿）。
 */
@DrawableRes
private fun BottomNavDestination.selectedIconRes(): Int? = when (this) {
    BottomNavDestination.Home -> R.drawable.ic_nav_home_selected
    BottomNavDestination.Calendar -> R.drawable.ic_nav_calendar_selected
    BottomNavDestination.Library -> R.drawable.ic_nav_library_selected
    BottomNavDestination.Profile -> R.drawable.ic_nav_profile_selected
    else -> null
}

// ─── 颜色：统一引用 ui/colors 设计令牌，随主题深浅自动解析 ────────────────────────

private val ColorSelected: Color
    @Composable @ReadOnlyComposable get() = IconColors.Success.default.current()   // #145436
private val ColorUnselected: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.default.current()    // #333
private val BarBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
/** 设计：收起态 FAB 描边 = border/focus/default（浅色 #000 / 深色白）。 */
private val FabBorder: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Focus.default.current()
/** 设计：展开态 FAB 描边 = palette/neutral-600 #808080（弱化，强调让位给速拨按钮）。 */
private val FabBorderExpanded: Color = Palette.neutral600
/** 设计（展开态 nav）：速拨子按钮描边 = palette/gray-600（#656565）。 */
private val SubButtonBorder: Color = Palette.gray600

// ─── 尺寸 ─────────────────────────────────────────────────────────────────────

// Figma nav 总高 209，其中底部安全区 34；Android inset 更大时再向上扩展。
private val NavContentHeight = 175.dp
private val NavItemsHeight = 58.dp   // Figma nav items：y=116..174
private val NavIconSize = 24.dp      // 四个底部 Tab 图标统一为 24dp
private val FabBottomAboveSafeArea = 34.dp // Figma FAB bottom=141，内容区 bottom=175
private val MinBottomSafeArea = 34.dp
private val FabSize = 65.dp        // 设计：vuesax/linear/scan size-[65px]
private val FabIconSize = 38.dp    // 设计：add size-[38px]
private val SubButtonSize = 48.dp  // 设计：ask/create size-[48px]
private val StrokeSm = 1.dp        // Figma 底部控件描边
private val FabShadow = 3.dp       // 设计：drop-shadow (0,3,3) neutral-400
private val SpeedDialGap = 16.dp     // 速拨相对 FAB 顶边的额外抬升
private val NavMaskHeight = 120.dp   // Figma mask_nav：底部内容渐隐区域

// ─── 主组件 ───────────────────────────────────────────────────────────────────

/**
 * 底部导航栏（home_final / nav）：中央下凹栏体 + 浮动「+」FAB，两侧各两个 tab。
 * 「+」点击展开 Ask Novie / Create 两个速拨子按钮。
 *
 * @param currentRoute 当前选中路由
 * @param onNavigate   tab 点击回调（Home/Calendar/Library/Profile）
 * @param onCreate     速拨 Create（新建笔记）
 * @param onAskNovie   速拨 Ask Novie
 * @param expanded         「+」速拨是否展开（受控，由外部持有以便全屏遮罩点击收起）
 * @param onExpandedChange 展开态变化回调
 */
@Composable
fun AppBottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onCreate: () -> Unit = {},
    onAskNovie: () -> Unit = {},
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 部分全面屏设备会返回 0dp；至少保留设计稿的 34dp，避免 Tab 和 FAB 贴住屏幕底边。
    val systemNavInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomSafeArea = systemNavInset.coerceAtLeast(MinBottomSafeArea)
    val navRootHeight = NavContentHeight + bottomSafeArea
    val navSurfaceHeight = NavItemsHeight + bottomSafeArea
    val fabBottomOffset = FabBottomAboveSafeArea + bottomSafeArea
    val speedDialTopOffset = fabBottomOffset + FabSize + SpeedDialGap
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(navRootHeight),
    ) {
        // 先铺内容到导航栏的渐隐遮罩，再绘制不透明的白色导航栏主体。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(NavMaskHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Palette.sand400,
                            Palette.sand400,
                        ),
                    ),
                ),
        )

        // 栏体 + 四个 tab（贴底、含安全区；点 tab 顺便收起速拨）
        CradleBar(
            currentRoute = currentRoute,
            barHeight = navSurfaceHeight,
            modifier = Modifier.align(Alignment.BottomCenter),
            onNavigate = { onExpandedChange(false); onNavigate(it) },
        )

        // 速拨子按钮（Ask / Create），展开时浮于 FAB 之上
        SpeedDialRow(
            visible = expanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -speedDialTopOffset),
            onAskNovie = { onExpandedChange(false); onAskNovie() },
            onCreate = { onExpandedChange(false); onCreate() },
        )

        // 中央「+」FAB（点击切换速拨）
        CenterFab(
            expanded = expanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -fabBottomOffset),
            onClick = { onExpandedChange(!expanded) },
        )
    }
}

// ─── 栏体（中央下凹 + 四个 tab） ───────────────────────────────────────────────

@Composable
private fun CradleBar(
    currentRoute: String,
    barHeight: Dp,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit,
) {
    val cradle = remember { CradleTopShape() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)              // 含底部安全区，栏体背景铺到屏幕底边
            .background(BarBg, cradle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NavItemsHeight),
            verticalAlignment = Alignment.Bottom,
        ) {
            // 左组：无左边距，Home / Calendar 平分左侧可用空间。
            Row(modifier = Modifier.weight(1f)) {
                NavTab(
                    BottomNavDestination.Home,
                    currentRoute,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigate,
                )
                NavTab(
                    BottomNavDestination.Calendar,
                    currentRoute,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigate,
                )
            }
            // 中央凹槽严格按 FAB 宽度让位，左右两组因此各占剩余空间的一半。
            Spacer(Modifier.width(FabSize))
            // 右组：Library / Profile 平分右侧可用空间。
            Row(modifier = Modifier.weight(1f)) {
                NavTab(
                    BottomNavDestination.Library,
                    currentRoute,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigate,
                )
                NavTab(
                    BottomNavDestination.Profile,
                    currentRoute,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigate,
                )
            }
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
    // 设计：展开时 FAB 描边弱化为 neutral-600 灰，收起时用 focus/default 黑。
    val fabBorder = if (expanded) FabBorderExpanded else FabBorder
    Surface(
        shape = CircleShape,
        color = BarBg,
        border = BorderStroke(StrokeSm, fabBorder),
        modifier = modifier
            .size(FabSize)
            .shadow(
                elevation = FabShadow,
                shape = CircleShape,
                spotColor = Palette.neutral400,
                ambientColor = Palette.neutral400,
            ),
    ) {
        Box(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_add),
                contentDescription = if (expanded) "Close" else "Create",
                tint = ColorUnselected,
                modifier = Modifier
                    .size(FabIconSize)
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
            SpeedDialButton(R.drawable.ic_ask_novie, "Ask Novie", iconSize = 28.dp, onClick = onAskNovie)
            SpeedDialButton(R.drawable.ic_nav_create, "Create", iconSize = 24.dp, onClick = onCreate)
        }
    }
}

@Composable
private fun SpeedDialButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    iconSize: Dp = 24.dp,
) {
    Surface(
        shape = CircleShape,
        color = BarBg,
        border = BorderStroke(StrokeSm, SubButtonBorder),
        modifier = Modifier.size(SubButtonSize),
    ) {
        Box(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                tint = ColorUnselected,
                modifier = Modifier.size(iconSize),
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
    // 选中态优先用设计 selected 专属双色图（不 tint）；否则用单色图靠 tint 上色。
    val selectedRes = destination.selectedIconRes()

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick(destination.route) },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp), // 设计：图标↔文字 gap 6
    ) {
        if (selected && selectedRes != null) {
            Icon(
                painter = painterResource(id = selectedRes),
                contentDescription = destination.label,
                tint = Color.Unspecified,   // 双色素材，保留自带绿+白
                modifier = Modifier.size(NavIconSize),
            )
        } else {
            Icon(
                painter = painterResource(id = destination.iconRes()),
                contentDescription = destination.label,
                tint = tint,
                modifier = Modifier.size(NavIconSize),
            )
        }
        Text(
            text = destination.label,
            fontSize = 12.sp,
            lineHeight = 15.sp,          // 设计：leading 1.25
            letterSpacing = 0.24.sp,     // 设计：tracking 0.24px
            fontWeight = weight,
            color = tint,
            maxLines = 1,
        )
    }
}

// ─── 中央下凹的顶边形状 ───────────────────────────────────────────────────────

/**
 * Figma Rectangle 3 的中央凹槽路径。控制点相对水平中心定位，因此任意屏宽下都与 FAB 同心；
 * 凹槽约 122dp 宽、46dp 深，为 65dp 圆形按钮及阴影留出平滑圆弧空间。
 */
private class CradleTopShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val scale = density.density

        fun x(offsetDp: Float) = cx + offsetDp * scale
        fun y(valueDp: Float) = valueDp * scale

        path.moveTo(0f, 0f)
        path.lineTo(x(-60.467f), 0f)
        path.cubicTo(
            x(-54.762f), 0f,
            x(-49.330f), y(2.436f),
            x(-45.535f), y(6.694f),
        )
        path.lineTo(x(-25.760f), y(28.886f))
        path.cubicTo(
            x(-10.250f), y(46.291f),
            x(17.296f), y(45.189f),
            x(31.366f), y(26.601f),
        )
        path.lineTo(x(45.498f), y(7.930f))
        path.cubicTo(
            x(49.279f), y(2.935f),
            x(55.181f), 0f,
            x(61.445f), 0f,
        )
        path.lineTo(w, 0f)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
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
