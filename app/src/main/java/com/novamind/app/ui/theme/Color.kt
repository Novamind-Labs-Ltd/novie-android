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

/**
 * Icon 语义色（来自 Figma「Icon」组），引用 [Palette]，各令牌区分 light/dark。
 */
object IconColors {
    object Brand {
        val default = DualColor(Palette.forrest600, Palette.forrest400)
        val ghost = DualColor(Palette.forrest50, Palette.white)
        val hover = DualColor(Palette.forrest500, Palette.white)
        val pressed = DualColor(Palette.forrest700, Palette.white)
        val secondary = DualColor(Palette.forrest100, Palette.white)
    }

    object BrandSecondary {
        val default = DualColor(Palette.teal600, Palette.teal600)
        val ghost = DualColor(Palette.teal50, Palette.teal50)
        val hover = DualColor(Palette.teal700, Palette.teal700)
        val pressed = DualColor(Palette.teal800, Palette.teal800)
        val secondary = DualColor(Palette.teal200, Palette.teal200)
    }

    object Default {
        val default = DualColor(Palette.neutral900, Palette.neutral50)
        val disabled = DualColor(Palette.neutral300, Palette.neutral600)
        val onColor = DualColor(Palette.white, Palette.white)
        val onDark = DualColor(Palette.white, Palette.neutral900)
        val secondary = DualColor(Palette.neutral600, Palette.neutral300)
        val tertiary = DualColor(Palette.neutral400, Palette.neutral500)
    }

    object Error {
        val default = DualColor(Palette.red600, Palette.red300)
        val onSurface = DualColor(Palette.white, Palette.red900)
    }

    object Info {
        val default = DualColor(Palette.slate700, Palette.slate300)
    }

    object Success {
        val default = DualColor(Palette.forrest700, Palette.forrest300)
        val onSurface = DualColor(Palette.white, Palette.forrest900)
    }

    object Warning {
        val default = DualColor(Palette.orange700, Palette.orange400)
        val onSurface = DualColor(Palette.white, Palette.orange900)
    }
}
