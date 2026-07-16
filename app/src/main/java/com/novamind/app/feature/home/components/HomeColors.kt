package com.novamind.app.feature.home.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.Palette
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current

/**
 * Home 模块共享配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析
 * （不使用硬编码颜色）。供 HomeScreen / UpcomingListScreen 与 components 下组件共用。
 */
internal val BgPage: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
internal val BgCard: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
internal val BgActionBar: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
internal val ColorTextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
internal val ColorTextSub: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
internal val ColorTextHint: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.tertiary.current()
internal val ColorAvatarBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Interactive.active.current()
internal val ColorBadge: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Error.default.current()
internal val ColorOnBadge: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.onColor.current()
internal val ColorMenuIcon: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.secondary.current()
internal val ColorBorder: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.default.current()
internal val ColorIconCircle: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.inset.current()

/** Up next 卡片左侧时间戳文字（Figma: text/warning/on-surface → orange-900）。 */
internal val ColorTimeStamp: Color
    @Composable @ReadOnlyComposable get() = TextColors.Warning.onSurface.current()
/** 深色胶囊按钮底色（Figma: button/primary/background-tertiary → gray-700）：Start notes / Ask Novie。 */
internal val ColorButtonDark: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Primary.backgroundTertiary.current()
/** 深底上的文字/图标色（Figma: text/primary/on-dark → sand-300）。 */
internal val ColorOnDark: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.onDark.current()

/** NoteCard 选中态边框：品牌浅绿（固定基础色）。 */
internal val ColorSelectedBorder = Palette.forrest200
