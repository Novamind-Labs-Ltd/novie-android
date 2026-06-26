package com.novamind.app.ui.colors

/**
 * Text 语义色（来自 Figma「text」组），引用 [Palette]，各令牌区分 light/dark。
 */
object TextColors {
    object Disabled {
        val default = DualColor(
            Palette.neutral400,
            Palette.neutral500
        )
        val onDisabled = DualColor(
            Palette.neutral500,
            Palette.neutral400
        )
    }

    object Error {
        val default =
            DualColor(Palette.red600, Palette.red300)
        val onSurface =
            DualColor(Palette.red900, Palette.red100)
    }

    object Info {
        val default = DualColor(
            Palette.slate800,
            Palette.slate300
        )
    }

    object Inverse {
        val default = DualColor(
            Palette.white,
            Palette.neutral900
        )
    }

    object Link {
        val default = DualColor(
            Palette.slate900,
            Palette.slate200
        )
        val visited = DualColor(
            Palette.slate700,
            Palette.slate400
        )
    }

    object Primary {
        val default = DualColor(
            Palette.neutral900,
            Palette.neutral50
        )
        val onDark = DualColor(
            Palette.sand300,
            Palette.neutral900
        )
        val secondary = DualColor(
            Palette.neutral700,
            Palette.neutral200
        )
        val tertiary = DualColor(
            Palette.neutral400,
            Palette.neutral500
        )
    }

    object Success {
        val default = DualColor(
            Palette.forrest600,
            Palette.forrest300
        )
        val onSurface = DualColor(
            Palette.forrest700,
            Palette.forrest200
        )
    }

    object Warning {
        val default = DualColor(
            Palette.orange700,
            Palette.orange400
        )
        val onSurface = DualColor(
            Palette.orange900,
            Palette.orange100
        )
    }
}
