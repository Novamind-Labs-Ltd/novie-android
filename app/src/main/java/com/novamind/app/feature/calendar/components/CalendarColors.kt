package com.novamind.app.feature.calendar.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current

/**
 * Calendar 模块共享配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析
 * （不使用硬编码颜色）。供 CalendarScreen 与 components 下各组件共用。
 */
internal val BgPage: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
internal val ColorSurface: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
internal val ColorTextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
internal val ColorTextSub: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
internal val ColorTextFaint: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.tertiary.current()
internal val ColorTextInverse: Color
    @Composable @ReadOnlyComposable get() = TextColors.Inverse.default.current()
internal val ColorTextError: Color
    @Composable @ReadOnlyComposable get() = TextColors.Error.default.current()
internal val ColorBorder: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.default.current()
internal val ColorPrimary: Color
    @Composable @ReadOnlyComposable get() = IconColors.Brand.default.current()
internal val ColorPrimaryBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Primary.default.current()
internal val ColorOnPrimary: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.onColor.current()
internal val MeetingBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Scenario.teal.current()
internal val MeetingIcon: Color
    @Composable @ReadOnlyComposable get() = IconColors.BrandSecondary.default.current()
internal val TodoBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.inset.current()
internal val TodoIcon: Color
    @Composable @ReadOnlyComposable get() = IconColors.Success.default.current()
