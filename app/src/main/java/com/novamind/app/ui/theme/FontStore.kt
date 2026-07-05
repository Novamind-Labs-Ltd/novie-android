package com.novamind.app.ui.theme

import androidx.compose.ui.text.font.FontFamily
import com.novamind.app.common.storage.MmkvStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 可切换的应用字体。用内置的通用 [FontFamily]，无需打包字体文件。
 */
enum class AppFont(val label: String, val family: FontFamily) {
    SYSTEM("系统默认", FontFamily.Default),
    SANS("Sans", FontFamily.SansSerif),
    SERIF("Serif", FontFamily.Serif),
    MONO("等宽", FontFamily.Monospace),
    CURSIVE("手写", FontFamily.Cursive),
}

/**
 * 应用字体的单一数据源（持久化到 MMKV，可观察）。
 *
 * 由 [AppTheme] 订阅并即时套用到 [androidx.compose.material3.Typography]，切换**立即生效**、无需重启。
 * 在 `Application.onCreate`（MMKV 初始化后）调用 [load] 载入持久化选择。
 */
object FontStore {

    private const val PREF = "ui_prefs"
    private const val KEY_FONT = "app_font"

    private val store by lazy { MmkvStore(PREF) }

    private val _font = MutableStateFlow(AppFont.SYSTEM)
    val font: StateFlow<AppFont> = _font.asStateFlow()

    /** 载入持久化的字体选择（脏数据回退系统默认）。 */
    fun load() {
        val saved = store.getString(KEY_FONT, null)
        _font.value = AppFont.entries.firstOrNull { it.name == saved } ?: AppFont.SYSTEM
    }

    /** 切换字体并持久化，立即生效。 */
    fun select(font: AppFont) {
        _font.value = font
        store.putString(KEY_FONT, font.name)
    }
}
