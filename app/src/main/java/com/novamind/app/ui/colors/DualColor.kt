package com.novamind.app.ui.colors

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 当前是否深色主题，由 [com.novamind.app.ui.theme.AppTheme] 提供，
 * 跟随 App 主题而非直接读系统设置（手动切主题时也能正确解析）。
 * 未被 AppTheme 包裹时默认浅色（false）。
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** 语义色：同一令牌的浅色/深色取值。用 [current] 按当前主题解析。 */
data class DualColor(val light: Color, val dark: Color)

@Composable
@ReadOnlyComposable
fun DualColor.current(): Color = if (LocalDarkTheme.current) dark else light
