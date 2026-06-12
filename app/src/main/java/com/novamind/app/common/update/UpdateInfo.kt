package com.novamind.app.common.update

/** 升级类型：无 / 可选 / 强制 */
enum class UpdateType { None, Optional, Force }

/** 一次升级策略结果（服务端下发，客户端只读） */
data class UpdateInfo(
    val type: UpdateType,
    val latestVersionName: String,
    val latestVersionCode: Int,
    val releaseNotes: String,
    /** 升级地址：应用市场页或自有 APK 直链 */
    val url: String,
)
