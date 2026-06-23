package com.novamind.app.feature.auth

import com.novamind.app.common.storage.MmkvStore

/**
 * 持久化「指纹登录」开关。存储到 MMKV（封装在 KeyValueStore 抽象层）。
 *
 * 注意：开关仅记录用户意愿。实际能否走指纹还取决于设备是否支持且已录入
 * （见 [AuthManager.isBiometricAvailable]）；二者都满足才真正启用。
 */
class BiometricPreferences {

    private val store = MmkvStore(PREFS_NAME)

    var enabled: Boolean
        get() = store.getBoolean(KEY_ENABLED, false)
        set(value) { store.putBoolean(KEY_ENABLED, value) }

    private companion object {
        const val PREFS_NAME = "biometric_prefs"
        const val KEY_ENABLED = "fingerprint_login_enabled"
    }
}
