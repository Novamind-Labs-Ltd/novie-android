package com.novamind.app.common.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * 推送通知渠道集中定义。
 *
 * [DEFAULT_ID] 同时用于：FcmService 前台/data 消息展示、Manifest 中 FCM 默认渠道 meta-data，
 * 以及应用启动时预创建——确保后台 notification 消息到达时渠道已存在（否则系统会落到杂项渠道）。
 */
object PushChannels {

    const val DEFAULT_ID = "fcm_default"

    /** 幂等创建默认推送渠道（minSdk 26，NotificationChannel 始终可用）。 */
    fun ensureDefault(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(DEFAULT_ID) != null) return
        val channel = NotificationChannel(
            DEFAULT_ID, "Push Notifications", NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "App message push notifications" }
        mgr.createNotificationChannel(channel)
    }
}
