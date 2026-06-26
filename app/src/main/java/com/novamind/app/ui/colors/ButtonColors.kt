package com.novamind.app.ui.colors


/**
 * Button 语义色（来自 Figma「button」组），引用 [Palette]，各令牌区分 light/dark。
 * brand / brandSecondary 按状态命名（default/ghost/hover/pressed/secondary）；
 * 其余按角色命名（background/border/text/…）。
 */
object ButtonColors {
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

    object Destructive {
        val background = DualColor(Palette.red600, Palette.red500)
        val backgroundDisabled = DualColor(Palette.red200, Palette.red800)
        val border = DualColor(Palette.red600, Palette.red500)
        val text = DualColor(Palette.white, Palette.white)
    }

    object Ghost {
        val background = DualColor(Palette.black0, Palette.black0)
        val border = DualColor(Palette.black0, Palette.black0)
        val text = DualColor(Palette.neutral700, Palette.neutral300)
    }

    object Primary {
        val background = DualColor(Palette.gray900, Palette.white)
        val backgroundDisabled = DualColor(Palette.neutral200, Palette.gray700)
        val backgroundSecondary = DualColor(Palette.gray800, Palette.gray100)
        val backgroundTertiary = DualColor(Palette.gray700, Palette.gray100)
        val border = DualColor(Palette.white, Palette.gray900)
        val text = DualColor(Palette.white, Palette.gray900)
        val textDisabled = DualColor(Palette.neutral400, Palette.neutral500)
    }

    object Secondary {
        val background = DualColor(Palette.black0, Palette.black0)
        val backgroundDisabled = DualColor(Palette.black50a, Palette.black30a)
        val border = DualColor(Palette.gray900, Palette.white)
        val borderDisabled = DualColor(Palette.neutral200, Palette.gray700)
        val text = DualColor(Palette.gray900, Palette.white)
        val textDisabled = DualColor(Palette.neutral400, Palette.neutral500)
    }

    object Success {
        val background = DualColor(Palette.green600, Palette.green600)
        val border = DualColor(Palette.green600, Palette.green600)
        val text = DualColor(Palette.white, Palette.white)
    }
}
