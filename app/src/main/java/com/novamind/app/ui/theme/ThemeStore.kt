package com.novamind.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.novamind.app.common.storage.MmkvStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 可切换的主题深浅模式。 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

/**
 * 应用主题模式的单一数据源（持久化到 MMKV，可观察）。**默认跟随系统**。
 *
 * 由 [AppTheme] 订阅并即时解析深浅色，切换立即生效、无需重启。
 * 在 `Application.onCreate`（MMKV 初始化后）调用 [load] 载入持久化选择。
 */
object ThemeStore {

    private const val PREF = "ui_prefs"
    private const val KEY_THEME = "theme_mode"

    private val store by lazy { MmkvStore(PREF) }

    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    /** 载入持久化的主题模式（脏数据回退跟随系统）。 */
    fun load() {
        val saved = store.getString(KEY_THEME, null)
        _mode.value = ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM
    }

    /** 切换主题模式并持久化，立即生效。 */
    fun select(mode: ThemeMode) {
        _mode.value = mode
        store.putString(KEY_THEME, mode.name)
    }
}

/**
 * 按 [ThemeStore] 的模式解析当前是否深色：跟随系统 / 强制浅色 / 强制深色。
 * 供应用入口（MainActivity）传给 `AppTheme(darkTheme = ...)`；预览不用它（走 AppTheme 默认跟随系统）。
 */
@Composable
fun rememberIsDarkTheme(): Boolean {
    val mode by ThemeStore.mode.collectAsState()
    return when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
}
