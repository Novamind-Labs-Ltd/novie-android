package com.novamind.app.feature.create.folder.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current

/**
 * 文件夹选择弹窗共享配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析
 * （不使用硬编码颜色）。供 FolderPickerSheet 与 components 下组件共用。
 */
internal val Primary: Color
    @Composable @ReadOnlyComposable get() = IconColors.Brand.default.current()
internal val TextDark: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
internal val TextHint: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.tertiary.current()
internal val SheetBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
internal val ItemBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
internal val RadioUnselected: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.tertiary.current()
