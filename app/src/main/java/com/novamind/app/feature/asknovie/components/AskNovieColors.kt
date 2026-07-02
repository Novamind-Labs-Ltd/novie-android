package com.novamind.app.feature.asknovie.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current

/**
 * AskNovie 模块共享配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析
 * （不使用硬编码颜色）。供 ChatHistorySheet 等界面与 components 下组件共用。
 */
internal val SheetBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
internal val TitleColor: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
internal val SubColor: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.tertiary.current()
internal val ItemColor: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
internal val DarkPill: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Primary.background.current()
internal val OnDarkPill: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Primary.text.current()
internal val SearchBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
internal val FieldBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.inset.current()
internal val FieldBorder: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.default.current()

// ── AskNovieScreen（聊天主界面）用色 ──
internal val Bg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
internal val Card: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
internal val TextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
internal val TextSub: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
internal val Dark: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Primary.background.current()
internal val OnDark: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Primary.text.current()
internal val ChipText: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
internal val SendGreen: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Success.background.current()
internal val OnSendGreen: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Success.text.current()
internal val MenuBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.secondary.current()
internal val AttachChipBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.inset.current()
internal val PlaceholderBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Interactive.active.current()
