package com.novamind.app.common.device

import android.content.Context
import com.novamind.app.common.storage.MmkvStore
import java.util.UUID

/**
 * 应用安装级唯一标识（UUID）。首次访问时生成并持久化，卸载重装后重新生成。
 * 与 ANDROID_ID 互补：可作为登录/风控的设备指纹之一。
 *
 * 存储：MMKV（mmapID "device"）。
 */
object InstallId {
    private const val PREF = "device"
    private const val KEY = "install_uuid"

    // MMKV.mmkvWithID 无需 Context；保留方法上的 context 参数仅为兼容既有调用方签名。
    private val store by lazy { MmkvStore(PREF) }

    fun get(@Suppress("UNUSED_PARAMETER") context: Context): String =
        store.getString(KEY, null) ?: UUID.randomUUID().toString().also {
            store.putString(KEY, it)
        }
}
