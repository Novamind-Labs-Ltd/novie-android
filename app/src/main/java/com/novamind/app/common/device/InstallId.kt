package com.novamind.app.common.device

import android.content.Context
import java.util.UUID

/**
 * 应用安装级唯一标识（UUID）。首次访问时生成并持久化，卸载重装后重新生成。
 * 与 ANDROID_ID 互补：可作为登录/风控的设备指纹之一。
 */
object InstallId {
    private const val PREF = "device"
    private const val KEY = "install_uuid"

    fun get(context: Context): String {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return sp.getString(KEY, null) ?: UUID.randomUUID().toString().also {
            sp.edit().putString(KEY, it).apply()
        }
    }
}
