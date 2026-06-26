package com.novamind.app.ui.colors

import androidx.compose.ui.graphics.Color

/**
 * 基础色阶（palette）——设计系统的「原始色」单一来源，取自 Figma Novie UI library。
 * 注意 forrest / whit 等为设计稿原始命名，保持一致。
 * 透明度色（如 black-30a）按 Figma 的 #RRGGBBAA 转为 Compose 的 0xAARRGGBB。
 */
object Palette {
    // black / white
    val black = Color(0xFF000000)
    val black0 = Color(0x1A000000)   // #0000001A
    val black30a = Color(0x4D000000) // #0000004D
    val black50a = Color(0x80000000) // #00000080
    val white = Color(0xFFFFFFFF)
    val white30a = Color(0x4DFFFFFF) // #FFFFFF4D
    val white50a = Color(0x80FFFFFF) // #FFFFFF80（whit-50a）

    // fern
    val fern50 = Color(0xFFF0F7F3)
    val fern100 = Color(0xFFE3F0EA)
    val fern200 = Color(0xFFC4DDD0)
    val fern300 = Color(0xFFA0C9B5)
    val fern400 = Color(0xFF8BBF9F)
    val fern500 = Color(0xFF6A9E84)
    val fern600 = Color(0xFF4E7D64)
    val fern700 = Color(0xFF2E5C42)
    val fern800 = Color(0xFF1C3D2B)
    val fern900 = Color(0xFF0E2419)

    // forrest（品牌绿）
    val forrest50 = Color(0xFFEAF4EE)
    val forrest100 = Color(0xFFC7E5D2)
    val forrest200 = Color(0xFF96CEB0)
    val forrest300 = Color(0xFF62B088)
    val forrest400 = Color(0xFF388E64)
    val forrest500 = Color(0xFF257550)
    val forrest600 = Color(0xFF1B6B45)
    val forrest700 = Color(0xFF145436)
    val forrest800 = Color(0xFF0D3D27)
    val forrest900 = Color(0xFF072719)

    // gray
    val gray0 = Color(0xFFFFFFFF)
    val gray50 = Color(0xFFF8F9FA)
    val gray100 = Color(0xFFF1F3F4)
    val gray200 = Color(0xFFE5E5E5)
    val gray300 = Color(0xFFD6D6D6)
    val gray400 = Color(0xFFAFAFAF)
    val gray500 = Color(0xFF898989)
    val gray600 = Color(0xFF656565)
    val gray650 = Color(0xFF5F6368)
    val gray700 = Color(0xFF3B3B3B)
    val gray800 = Color(0xFF242424)
    val gray900 = Color(0xFF000000)

    // green
    val green50 = Color(0xFFF4F7EE)
    val green100 = Color(0xFFE6EDD6)
    val green200 = Color(0xFFCCDDB0)
    val green300 = Color(0xFFABCA80)
    val green400 = Color(0xFF88B24E)
    val green500 = Color(0xFF6A9232)
    val green600 = Color(0xFF567828)
    val green700 = Color(0xFF4A6228)
    val green800 = Color(0xFF364A1E)
    val green900 = Color(0xFF233214)

    // neutral
    val neutral50 = Color(0xFFECECEC)
    val neutral100 = Color(0xFFD9D9D9)
    val neutral200 = Color(0xFFC7C7C7)
    val neutral300 = Color(0xFFB4B4B4)
    val neutral400 = Color(0xFFA3A3A3)
    val neutral500 = Color(0xFF919191)
    val neutral600 = Color(0xFF808080)
    val neutral700 = Color(0xFF656565)
    val neutral800 = Color(0xFF4C4C4C)
    val neutral900 = Color(0xFF333333)

    // orange
    val orange50 = Color(0xFFFFEFDE)
    val orange100 = Color(0xFFFFDEBD)
    val orange200 = Color(0xFFFFCE9D)
    val orange300 = Color(0xFFFFBE7D)
    val orange400 = Color(0xFFFFAD5D)
    val orange500 = Color(0xFFFF9D3A)
    val orange600 = Color(0xFFFF8C00)
    val orange700 = Color(0xFFC76F0E)
    val orange800 = Color(0xFF925312)
    val orange900 = Color(0xFF603812)

    // red
    val red50 = Color(0xFFFDF1EC)
    val red100 = Color(0xFFFAD8CC)
    val red200 = Color(0xFFF5B5A0)
    val red300 = Color(0xFFEC8C72)
    val red400 = Color(0xFFE06040)
    val red500 = Color(0xFFC8391A)
    val red600 = Color(0xFFA82E12)
    val red700 = Color(0xFF872410)
    val red800 = Color(0xFF651B0C)
    val red900 = Color(0xFF421208)

    // sand
    val sand50 = Color(0xFFFEFEFE)
    val sand100 = Color(0xFFFDFCF9)
    val sand200 = Color(0xFFFDFBF8)
    val sand300 = Color(0xFFFCFAF6)
    val sand400 = Color(0xFFF3F1EB)
    val sand500 = Color(0xFFECEAE7)
    val sand550 = Color(0xFFE0DED9)
    val sand600 = Color(0xFFD5D3CE)
    val sand700 = Color(0xFFBFBDB8)
    val sand800 = Color(0xFFA9A7A2)
    val sand900 = Color(0xFF8C8A85)

    // slate
    val slate50 = Color(0xFFEAECEE)
    val slate100 = Color(0xFFD4D9DE)
    val slate200 = Color(0xFFC0C7CE)
    val slate300 = Color(0xFFABB4BE)
    val slate400 = Color(0xFF97A3AF)
    val slate500 = Color(0xFF83919F)
    val slate600 = Color(0xFF708090)
    val slate700 = Color(0xFF596571)
    val slate800 = Color(0xFF434C54)
    val slate900 = Color(0xFF2E3339)

    // teal
    val teal50 = Color(0xFFEEF7FA)
    val teal100 = Color(0xFFD3EBF2)
    val teal200 = Color(0xFFA9D6E5)
    val teal300 = Color(0xFF6FBFD8)
    val teal400 = Color(0xFF32B4D9)
    val teal500 = Color(0xFF3CA3C1)
    val teal600 = Color(0xFF4A8292)
    val teal700 = Color(0xFF326776)
    val teal800 = Color(0xFF224D59)
    val teal900 = Color(0xFF12333C)
}
