package com.novamind.app.ui.theme

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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
    SYSTEM("系统默认"),
    SANS("Sans"),
    SERIF("Serif"),
    MONO("等宽"),
    CURSIVE("手写"),
}

/**
 * 字体族解析器：把 [AppFont] 解析为可用的 [FontFamily]。
 *
 * Google Sans Flex 为可变字体，从 `assets/fonts/google_sans_flex.ttf` 加载（OFL 开源，见文档），
 * 缺失时回退 [FontFamily.Default]（英文/中文均由系统字体渲染），保证无字体文件也能编译运行。
 */
object AppFonts {

    private const val ASSET_PATH = "fonts/google_sans_flex.ttf"
    private const val TAG = "AppFonts"

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
            gsfFamily = runCatching {
                val am = context.applicationContext.assets
                am.open(ASSET_PATH).close() // 探测存在性；不存在则抛异常走回退
                FontFamily(Font(path = ASSET_PATH, assetManager = am))
            }.onFailure {
                AppLog.w(TAG) { "Google Sans Flex 未内置($ASSET_PATH)，回退系统字体：${it.message}" }
            }.getOrNull()
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
