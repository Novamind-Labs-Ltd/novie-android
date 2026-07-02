package com.novamind.app.debug.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.novamind.app.MainActivity
import com.novamind.app.R

/**
 * 通知调试工具（仅 Debug 包）：验证悬浮（heads-up）、锁屏可见性、熄屏点亮、桌面角标。
 *
 * 说明：
 * - 渠道的 importance / 锁屏可见性在**创建后不可改**，[ensureChannels] 每次先删再建，
 *   保证调试时改动生效（会重置用户对渠道的手动设置，Debug 场景可接受）。
 * - 延迟发送用主线程 Handler，面板关闭后仍会触发；进程被杀则取消（Debug 场景可接受）。
 * - 角标/熄屏点亮为厂商行为：Pixel 长按图标看计数点，小米/三星等各有实现，结果仅供参考。
 */
object NotificationDebugger {

    private const val CH_HEADS_UP = "debug_heads_up"
    private const val CH_LOCK_PUBLIC = "debug_lock_public"
    private const val CH_LOCK_PRIVATE = "debug_lock_private"
    private const val CH_LOCK_SECRET = "debug_lock_secret"
    private const val CH_BADGE = "debug_badge"
    private const val CH_FULLSCREEN = "debug_fullscreen"

    private const val ID_HEADS_UP = 9001
    private const val ID_LOCK = 9002
    private const val ID_SCREEN_OFF = 9003
    private const val ID_BADGE = 9004

    /** 延迟发送的等待时长：给用户留出锁屏/熄屏的操作时间。 */
    private const val DELAY_MS = 5_000L

    private val handler = Handler(Looper.getMainLooper())

    /** 重建全部调试渠道（先删后建，让 importance/锁屏可见性等设置生效）。 */
    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        listOf(
            CH_HEADS_UP, CH_LOCK_PUBLIC, CH_LOCK_PRIVATE, CH_LOCK_SECRET, CH_BADGE, CH_FULLSCREEN,
        ).forEach { nm.deleteNotificationChannel(it) }

        nm.createNotificationChannel(
            NotificationChannel(CH_HEADS_UP, "Debug·悬浮通知", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "IMPORTANCE_HIGH，亮屏时应弹横幅" },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_LOCK_PUBLIC, "Debug·锁屏公开", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_LOCK_PRIVATE, "Debug·锁屏隐藏内容", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_LOCK_SECRET, "Debug·锁屏不显示", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_BADGE, "Debug·角标", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { setShowBadge(true) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_FULLSCREEN, "Debug·熄屏全屏", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "带 fullScreenIntent，熄屏时应点亮并全屏拉起" },
        )
    }

    fun areEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** 打开本应用的系统通知设置页。 */
    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** 悬浮通知：亮屏状态下应弹出横幅（channel IMPORTANCE_HIGH + PRIORITY_HIGH）。 */
    fun postHeadsUp(context: Context) {
        notify(
            context, ID_HEADS_UP,
            builder(context, CH_HEADS_UP, "悬浮通知", "IMPORTANCE_HIGH：亮屏时应弹出横幅")
                .setPriority(NotificationCompat.PRIORITY_HIGH),
        )
    }

    /**
     * 锁屏通知：延迟 [DELAY_MS] 发送，期间请先锁屏，观察锁屏上的展示差异。
     * @param visibility [NotificationCompat.VISIBILITY_PUBLIC] 完整显示 /
     *   [NotificationCompat.VISIBILITY_PRIVATE] 隐藏内容 / [NotificationCompat.VISIBILITY_SECRET] 不显示。
     */
    fun postLockScreenDelayed(context: Context, visibility: Int) {
        val (channel, label) = when (visibility) {
            NotificationCompat.VISIBILITY_PUBLIC -> CH_LOCK_PUBLIC to "公开（完整显示）"
            NotificationCompat.VISIBILITY_PRIVATE -> CH_LOCK_PRIVATE to "隐藏内容"
            else -> CH_LOCK_SECRET to "不显示"
        }
        val appContext = context.applicationContext
        handler.postDelayed({
            notify(
                appContext, ID_LOCK,
                builder(appContext, channel, "锁屏通知 · $label", "lockscreenVisibility=$visibility")
                    .setVisibility(visibility),
            )
        }, DELAY_MS)
    }

    /**
     * 熄屏通知：延迟 [DELAY_MS] 发送，期间请熄屏。
     * @param fullScreen true 时携带 fullScreenIntent（熄屏应点亮并直接拉起 MainActivity，
     *   类似来电；API 34+ 需在系统设置授予"全屏通知"权限，否则降级为悬浮横幅）。
     */
    fun postScreenOffDelayed(context: Context, fullScreen: Boolean) {
        val appContext = context.applicationContext
        handler.postDelayed({
            val b = builder(
                appContext,
                if (fullScreen) CH_FULLSCREEN else CH_HEADS_UP,
                if (fullScreen) "熄屏全屏通知" else "熄屏通知",
                if (fullScreen) "fullScreenIntent：熄屏应点亮并拉起页面" else "熄屏后发送：观察是否点亮屏幕/出现在锁屏",
            ).setPriority(NotificationCompat.PRIORITY_HIGH)
            if (fullScreen) {
                b.setFullScreenIntent(mainActivityPi(appContext), true)
            }
            notify(appContext, ID_SCREEN_OFF, b)
        }, DELAY_MS)
    }

    /** 桌面角标：channel showBadge + setNumber。是否显示数字取决于启动器。 */
    fun postBadge(context: Context, count: Int) {
        notify(
            context, ID_BADGE,
            builder(context, CH_BADGE, "角标通知", "setNumber($count)，长按桌面图标或看角标数字")
                .setNumber(count),
        )
    }

    /** 清除本工具发出的全部通知（角标随之消失）。 */
    fun clearAll(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        listOf(ID_HEADS_UP, ID_LOCK, ID_SCREEN_OFF, ID_BADGE).forEach { nm.cancel(it) }
    }

    private fun builder(
        context: Context,
        channel: String,
        title: String,
        text: String,
    ): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPi(context))

    private fun mainActivityPi(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notify(context: Context, id: Int, builder: NotificationCompat.Builder) {
        // POST_NOTIFICATIONS 未授权时 notify 会被系统静默丢弃，此处不做额外处理（面板有状态提示）。
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }
}
