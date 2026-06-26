package com.novamind.app.ui.colors


/**
 * Border 语义色（来自 Figma「border」组），引用 [Palette]，各令牌区分 light/dark。
 */
object BorderColors {
    object Default {
        val default = DualColor(Palette.neutral200, Palette.gray600)
        val disabled = DualColor(Palette.neutral100, Palette.gray700)
        val secondary = DualColor(Palette.neutral100, Palette.gray700)
        val strong = DualColor(Palette.neutral400, Palette.gray500)
    }

    object Error {
        val default = DualColor(Palette.red500, Palette.red400)
        val secondary = DualColor(Palette.red300, Palette.red700)
    }

    object Focus {
        val default = DualColor(Palette.gray900, Palette.white)
        val error = DualColor(Palette.red500, Palette.red400)
    }

    object Info {
        val default = DualColor(Palette.slate600, Palette.slate400)
    }

    object Input {
        val active = DualColor(Palette.gray900, Palette.white)
        val default = DualColor(Palette.neutral200, Palette.gray600)
        val disabled = DualColor(Palette.neutral100, Palette.gray700)
        val error = DualColor(Palette.red500, Palette.red400)
    }

    /** Figma 标注为 “outling”（即 outline）。 */
    object Outline {
        val inverse = DualColor(Palette.white, Palette.gray900)
        val primary = DualColor(Palette.gray900, Palette.white)
    }

    /** 场景色（scenario）：label 用色族名（forest 即 forrest）。 */
    object Scenario {
        val forest = DualColor(Palette.forrest500, Palette.forrest400)
        val orange = DualColor(Palette.orange600, Palette.orange400)
        val red = DualColor(Palette.red500, Palette.red400)
        val slate = DualColor(Palette.slate600, Palette.slate400)
    }

    object Success {
        val default = DualColor(Palette.forrest600, Palette.forrest500)
        val secondary = DualColor(Palette.forrest400, Palette.forrest700)
    }

    object Warning {
        val default = DualColor(Palette.orange600, Palette.orange500)
    }
}
