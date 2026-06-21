package com.novamind.app.feature.auth

import android.content.Context

/**
 * 持久化「指纹登录」开关。用轻量 SharedPreferences，不引入额外依赖。
 *
 * 注意：开关仅记录用户意愿。实际能否走指纹还取决于设备是否支持且已录入
 * （见 [AuthManager.isBiometricAvailable]）；二者都满足才真正启用。
 */
class BiometricPreferences(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "biometric_prefs"
        const val KEY_ENABLED = "fingerprint_login_enabled"
    }
}
