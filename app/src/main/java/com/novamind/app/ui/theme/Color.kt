package com.novamind.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Material3 默认主题色（被 Theme.kt 引用，暂保留）
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/** 语义色：同一令牌的浅色/深色取值。用 [current] 按系统主题解析。 */
data class DualColor(val light: Color, val dark: Color)

@Composable
@ReadOnlyComposable
fun DualColor.current(): Color = if (isSystemInDarkTheme()) dark else light
