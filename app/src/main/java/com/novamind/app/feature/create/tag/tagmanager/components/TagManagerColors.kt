package com.novamind.app.feature.create.tag.tagmanager.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current

/**
 * Tag manager 模块共享配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析
 * （不使用硬编码颜色）。供 TagManagerScreen / CreateTagSheet 与 components 下组件共用。
 */
internal val BgPage: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
internal val BgCard: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
internal val ColorTextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
internal val ColorTextSub: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
internal val ColorTextHint: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.tertiary.current()
internal val ColorBorder: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.default.current()
internal val ColorAccent: Color
    @Composable @ReadOnlyComposable get() = IconColors.Brand.default.current()
