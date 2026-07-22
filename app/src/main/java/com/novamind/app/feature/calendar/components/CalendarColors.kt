package com.novamind.app.feature.calendar.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.Palette
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
// 已完成条目圆形描边（Figma border/default/strong #a3a3a3）
internal val ColorBorderStrong: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.strong.current()
// 统计卡选中描边（Figma border/focus/default 黑色）
internal val ColorBorderFocus: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Focus.default.current()
internal val ColorPrimary: Color
    @Composable @ReadOnlyComposable get() = IconColors.Brand.default.current()
internal val ColorPrimaryBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Primary.default.current()
// 日期卡底色（Figma background/page/default/secondary #fcfaf6）
internal val ColorCardBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.secondary.current()
// 选中日 / 连接按钮的深色（Figma #242424 = gray800，固定深色以配白字）
internal val ColorDark: Color = Palette.gray800
internal val ColorOnPrimary: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.onColor.current()
// 统计卡底色（Figma：会议 green-100 #E6EDD6 / 待办 gray-200 #E5E5E5）
internal val MeetingBg: Color = Palette.green100
internal val TodoBg: Color = Palette.gray200
// 事件/任务行图标色（供 EventRow / TaskRow / CalendarIllustration 复用）
internal val MeetingIcon: Color
    @Composable @ReadOnlyComposable get() = IconColors.BrandSecondary.default.current()
internal val TodoIcon: Color
    @Composable @ReadOnlyComposable get() = IconColors.Success.default.current()
// To-do 卡片已完成态底色（Figma 1032-43576 background/interactive/tertiary #fcfaf6）
internal val TodoDoneCardBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Interactive.tertiary.current()
// To-do 勾选圈「已完成」实心底色（Figma icon/info/default #596571）
internal val TodoCheckedBg: Color
    @Composable @ReadOnlyComposable get() = IconColors.Info.default.current()
