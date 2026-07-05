package com.novamind.app.common.push

import com.novamind.app.common.log.AppLog
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.novamind.app.MainActivity
import com.novamind.app.R

/**
 * Firebase Cloud Messaging 接收服务。
 *
 * - [onNewToken]：设备令牌刷新时回调，需上报服务端以便定向推送（当前仅记录，待接入上报接口）；
 * - [onMessageReceived]：前台收到消息时回调（应用在后台时，仅含 notification 负载的消息由系统直接展示，
 *   不会进入此方法；含 data 负载的消息始终进入此方法）。这里统一据 title/body 构建一条通知。
 *
 * 通知权限（POST_NOTIFICATIONS）已在 Manifest 声明，运行时申请沿用应用既有逻辑。
 */
class FcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        AppLog.i(TAG) { "FCM token refreshed: ${token.take(12)}…" }
        // TODO: 上报 token 到服务端（绑定用户/设备），用于定向推送
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: return
        AppLog.i(TAG) { "FCM message received: $title" }
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        PushChannels.ensureDefault(this)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, PushChannels.DEFAULT_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        // 通知权限未授予时 notify 会被系统静默忽略，这里无需额外判断
        NotificationManagerCompat.from(this).notify(NOTIF_ID, notification)
    }

    companion object {
        private const val TAG = "Fcm"
        private const val NOTIF_ID = 2001
    }
}
