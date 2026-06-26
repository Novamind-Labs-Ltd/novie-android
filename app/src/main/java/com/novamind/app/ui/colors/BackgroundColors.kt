package com.novamind.app.ui.colors


/**
 * Background 语义色（来自 Figma「background」组），引用 [Palette]，各令牌区分 light/dark。
 */
object BackgroundColors {
    object Error {
        val default = DualColor(Palette.red500, Palette.red500)
        val secondary = DualColor(Palette.red200, Palette.red800)
        val tertiary = DualColor(Palette.red50, Palette.red800)
    }

    object Info {
        val default = DualColor(Palette.slate700, Palette.slate400)
        // Figma 标注为 “defualt 3”：最浅的一档 info 背景
        val default3 = DualColor(Palette.slate50, Palette.slate800)
        val secondary = DualColor(Palette.slate200, Palette.slate800)
    }

    object Interactive {
        val active = DualColor(Palette.sand550, Palette.gray600)
        val default = DualColor(Palette.white, Palette.gray800)
        val disabled = DualColor(Palette.neutral200, Palette.gray700)
        val secondary = DualColor(Palette.sand400, Palette.gray700)
        val tertiary = DualColor(Palette.sand300, Palette.gray600)
    }

    object Page {
        val default = DualColor(Palette.sand400, Palette.gray900)
        val secondary = DualColor(Palette.sand300, Palette.gray800)
        val tertiary = DualColor(Palette.sand500, Palette.gray700)
    }

    object Primary {
        val default = DualColor(Palette.gray900, Palette.white)
        val secondary = DualColor(Palette.gray100, Palette.gray800)
        val tertiary = DualColor(Palette.gray200, Palette.gray700)
    }

    /** 场景色（scenario）：label 用色族名，取值见 Palette。 */
    object Scenario {
        val fern = DualColor(Palette.forrest100, Palette.forrest800)
        val orange = DualColor(Palette.orange100, Palette.orange900)
        val red = DualColor(Palette.red100, Palette.red800)
        val slate = DualColor(Palette.slate100, Palette.slate800)
        val teal = DualColor(Palette.teal100, Palette.teal100)
    }

    object Success {
        val default = DualColor(Palette.green600, Palette.green600)
        val secondary = DualColor(Palette.green400, Palette.green700)
        val tertiary = DualColor(Palette.green100, Palette.green800)
    }

    object Surface {
        val default = DualColor(Palette.white, Palette.gray800)
        val elevated = DualColor(Palette.gray50, Palette.gray700)
        val inset = DualColor(Palette.sand500, Palette.gray700)
        val overlay = DualColor(Palette.black50a, Palette.black50a)
    }

    object Warning {
        val default = DualColor(Palette.orange500, Palette.orange500)
        val secondary = DualColor(Palette.orange300, Palette.orange800)
        val tertiary = DualColor(Palette.orange100, Palette.orange900)
    }
}
