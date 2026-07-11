package com.novamind.app.ui.theme

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.novamind.app.common.log.AppLog
import com.novamind.app.common.storage.MmkvStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 可切换的应用字体。
 *
 * - [GOOGLE_SANS_FLEX]：App **优先/默认**字体，从 assets 加载（见 [AppFonts]），文件缺失时回退系统字体。
 * - 其余为内置通用 [FontFamily]，无需字体文件。
 *
 * 注意：这里只存标签；真正的 [FontFamily] 由 [AppFonts.family] 按 Context 解析（Google Sans Flex 需读 assets）。
 */
enum class AppFont(val label: String) {
    GOOGLE_SANS_FLEX("Google Sans Flex"),
    SYSTEM("System default"),
    SANS("Sans"),
    SERIF("Serif"),
    MONO("Monospace"),
    CURSIVE("Handwriting"),
}

/**
 * 字体族解析器：把 [AppFont] 解析为可用的 [FontFamily]。
 *
 * Google Sans Flex 从 `assets/fonts/` 加载，**按字重多文件组装**（Fontsource 静态拆分版即每字重一个文件）：
 * - `google_sans_flex_regular.ttf`(400)、`_medium.ttf`(500)、`_bold.ttf`(700) —— 有几个用几个；
 * - 兼容单文件 `google_sans_flex.ttf`（可变字体或仅 Regular）。
 * 全部缺失时回退 [FontFamily.Default]（英文/中文均由系统字体渲染），保证无字体文件也能编译运行。
 */
object AppFonts {

    private const val TAG = "AppFonts"

    /** 候选字体文件（按字重）。存在哪个用哪个；顺序不影响。 */
    private val WEIGHTED_ASSETS = listOf(
        "fonts/google_sans_flex_regular.ttf" to FontWeight.Normal,   // 400
        "fonts/google_sans_flex_medium.ttf" to FontWeight.Medium,    // 500
        "fonts/google_sans_flex_semibold.ttf" to FontWeight.SemiBold,// 600
        "fonts/google_sans_flex_bold.ttf" to FontWeight.Bold,        // 700
    )

    /** 兼容：单文件（可变字体或仅 Regular）。 */
    private const val SINGLE_ASSET = "fonts/google_sans_flex.ttf"

    @Volatile
    private var gsfFamily: FontFamily? = null
    @Volatile
    private var gsfResolved = false

    fun family(context: Context, font: AppFont): FontFamily = when (font) {
        AppFont.GOOGLE_SANS_FLEX -> googleSansFlex(context) ?: FontFamily.Default
        AppFont.SYSTEM -> FontFamily.Default
        AppFont.SANS -> FontFamily.SansSerif
        AppFont.SERIF -> FontFamily.Serif
        AppFont.MONO -> FontFamily.Monospace
        AppFont.CURSIVE -> FontFamily.Cursive
    }

    /** Google Sans Flex 是否已就绪（assets 中存在字体文件）。供 UI 提示用。 */
    fun isGoogleSansFlexAvailable(context: Context): Boolean = googleSansFlex(context) != null

    /** 懒加载 + 缓存：仅首次探测 assets，缺失则返回 null（回退系统字体）。 */
    private fun googleSansFlex(context: Context): FontFamily? {
        gsfFamily?.let { return it }
        if (gsfResolved) return null
        synchronized(this) {
            if (gsfResolved) return gsfFamily
            gsfResolved = true
            val am = context.applicationContext.assets
            // 1) 优先按字重多文件组装（存在哪个用哪个）
            val fonts = WEIGHTED_ASSETS.mapNotNull { (path, weight) ->
                runCatching { am.open(path).close(); Font(path = path, assetManager = am, weight = weight) }
                    .getOrNull()
            }
            gsfFamily = when {
                fonts.isNotEmpty() -> FontFamily(fonts)
                // 2) 回退单文件（可变字体 / 仅 Regular）
                runCatching { am.open(SINGLE_ASSET).close() }.isSuccess ->
                    FontFamily(Font(path = SINGLE_ASSET, assetManager = am))
                else -> {
                    AppLog.w(TAG) { "Google Sans Flex 未内置(assets/fonts/*)，回退系统字体" }
                    null
                }
            }
            return gsfFamily
        }
    }
}

/**
 * 应用字体的单一数据源（持久化到 MMKV，可观察）。**默认 Google Sans Flex**。
 *
 * 由 [AppTheme] 订阅并即时套用到 Typography，切换立即生效、无需重启。
 * 在 `Application.onCreate`（MMKV 初始化后）调用 [load] 载入持久化选择。
 */
object FontStore {

    private const val PREF = "ui_prefs"
    private const val KEY_FONT = "app_font"

    private val store by lazy { MmkvStore(PREF) }

    private val _font = MutableStateFlow(AppFont.GOOGLE_SANS_FLEX)
    val font: StateFlow<AppFont> = _font.asStateFlow()

    /** 载入持久化的字体选择（脏数据回退默认 Google Sans Flex）。 */
    fun load() {
        val saved = store.getString(KEY_FONT, null)
        _font.value = AppFont.entries.firstOrNull { it.name == saved } ?: AppFont.GOOGLE_SANS_FLEX
    }

    /** 切换字体并持久化，立即生效。 */
    fun select(font: AppFont) {
        _font.value = font
        store.putString(KEY_FONT, font.name)
    }
}
