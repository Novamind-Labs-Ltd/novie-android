package com.novamind.app.feature.asknovie.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.DualColor
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.Palette
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
// 快捷建议 chip 描边（Figma forrest-900），深色主题降到浅绿以保持可见。
private val ChipBorderColor = DualColor(Palette.forrest900, Palette.forrest200)
internal val ChipBorder: Color
    @Composable @ReadOnlyComposable get() = ChipBorderColor.current()
// 输入区圆形图标按钮 / 模型胶囊底色（Figma background/primary/secondary #F1F3F4）。
internal val IconChipBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Primary.secondary.current()
// 输入框占位文案（Figma text/primary/tertiary #A3A3A3）。
internal val Hint: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.tertiary.current()
// 生成的笔记卡片日期文案色（暖棕，呼应笔记列表元信息）。
private val NoteDateColor = DualColor(Palette.orange800, Palette.orange300)
internal val NoteDate: Color
    @Composable @ReadOnlyComposable get() = NoteDateColor.current()
// 象限图高亮单元格：浅绿底 + 品牌绿文字/描边。
internal val QuadrantHighlightBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Success.tertiary.current()
internal val QuadrantHighlightText: Color
    @Composable @ReadOnlyComposable get() = TextColors.Success.default.current()
// 发送按钮绿（Figma icon/brand/default #1B6B45 = forrest-600）。
internal val SendGreen: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Brand.default.current()
internal val OnSendGreen: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.onColor.current()
// 发送按钮禁用态（无输入内容）：中性灰底 + 中性灰图标（设计系统 button/primary/disabled）。
internal val DisabledBtnBg: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Primary.backgroundDisabled.current()
internal val DisabledBtnIcon: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Primary.textDisabled.current()
internal val MenuBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.secondary.current()
internal val AttachChipBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.inset.current()
internal val PlaceholderBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Interactive.active.current()
