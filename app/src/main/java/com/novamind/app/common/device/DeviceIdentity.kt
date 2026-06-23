package com.novamind.app.common.device

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.Locale
import java.util.TimeZone

/**
 * 设备标识统一提供器（P0）。
 *
 * 聚合端上可用因子（安装 UUID + ANDROID_ID + 基础设备信息），供登录、JSBridge、
 * Debug、埋点复用，避免各处各自采集导致口径不一致。
 *
 * 注意：
 * - 这里的因子仅作采集，**最终唯一性以服务端聚合下发的 deviceId 为准**。
 * - 安装 UUID 当前用明文 MMKV（见 InstallId），加密存储留 P1（MmkvStore 支持 cryptKey）。
 * - 多进程一致性：应由主进程统一读取，`:web` 等子进程经 ContentProvider 获取，
 *   不要各进程各自读本地存储（MMKV 默认单进程模式）。
 */
object DeviceIdentity {

    @Volatile
    private var cached: DeviceFingerprint? = null

    /** 本地设备主标识：当前用安装 UUID（后续可替换为服务端下发的 deviceId）。 */
    fun localDeviceId(context: Context): String = InstallId.get(context)

    fun fingerprint(context: Context): DeviceFingerprint =
        cached ?: build(context.applicationContext).also { cached = it }

    @SuppressLint("HardwareIds")
    private fun build(context: Context): DeviceFingerprint {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: "unknown"

        val dm = context.resources.displayMetrics
        val screen = "${dm.widthPixels}x${dm.heightPixels} @${dm.density}x"

        return DeviceFingerprint(
            installUuid = InstallId.get(context),
            androidId = androidId,
            brand = Build.BRAND,
            model = Build.MODEL,
            osVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            screen = screen,
            timezone = TimeZone.getDefault().id,
            language = Locale.getDefault().toLanguageTag(),
        )
    }
}
