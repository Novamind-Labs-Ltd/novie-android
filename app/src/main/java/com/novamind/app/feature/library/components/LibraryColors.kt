package com.novamind.app.feature.library.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current

// 配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析（不使用硬编码颜色）
internal val BgPage: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
internal val BgCard: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
// 文件夹行底色（Figma background/page/default/secondary #fcfaf6）：比页面底色略浅的填充，无描边无投影
internal val BgRow: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.secondary.current()
internal val ColorTextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
internal val ColorTextSub: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
internal val ColorBorder: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.default.current()
internal val ColorAccent: Color
    @Composable @ReadOnlyComposable get() = IconColors.Brand.default.current()
internal val ColorIconDefault: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.default.current()
internal val ColorIconBtn: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
// 笔记卡片日期文案色（Figma orange-900 #603812）
internal val ColorDate: Color
    @Composable @ReadOnlyComposable get() = TextColors.Warning.onSurface.current()
