package com.novamind.app.ui.colors

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/** 语义色：同一令牌的浅色/深色取值。用 [current] 按系统主题解析。 */
data class DualColor(val light: Color, val dark: Color)

@Composable
@ReadOnlyComposable
fun DualColor.current(): Color = if (isSystemInDarkTheme()) dark else light
