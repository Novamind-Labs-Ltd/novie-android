package com.novamind.app.ui.colors

/**
 * Shadow 语义色（来自 Figma「shadow」组），引用 [Palette]，各令牌区分 light/dark。
 */
object ShadowColors {
    val default =
        DualColor(Palette.black30a, Palette.black50a)
    val lg =
        DualColor(Palette.black50a, Palette.black50a)
    val sm =
        DualColor(Palette.black30a, Palette.black50a)
}
