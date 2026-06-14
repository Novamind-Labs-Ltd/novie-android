package com.novamind.app.common.device

/**
 * 端上采集的设备因子集合。最终设备唯一性应以服务端聚合下发的 deviceId 为准
 * （见 Notion《Android 设备唯一性设计方案》），这里仅做客户端采集。
 */
data class DeviceFingerprint(
    val installUuid: String,   // 安装级 UUID（本地持久化，重装会变）
    val androidId: String,     // Settings.Secure.ANDROID_ID（Android 8+ 按签名隔离）
    val brand: String,         // Build.BRAND
    val model: String,         // Build.MODEL
    val osVersion: String,     // Android 版本
    val sdkInt: Int,           // API level
    val screen: String,        // 形如 1080x2340 @2.75x
    val timezone: String,      // 时区 id
    val language: String,      // BCP-47 语言标签
)
