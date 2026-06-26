package com.novamind.app.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.toColorInt

object ColorUtils {

    /** 把 #RRGGBB 解析为 Color；失败返回 null。供选色与卡片渲染共用。 */
    fun parseHexColor(hex: String?): Color? {
        if (hex.isNullOrBlank()) return null
        return runCatching { Color(hex.toColorInt()) }.getOrNull()
    }
    /** Color → "#RRGGBB"（忽略透明度）。仅在落库（borderColorHex）边界使用。 */
    fun Color.toHex(): String = "#%06X".format(0xFFFFFF and toArgb())


}