package com.novamind.app.common.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.novamind.app.R
import com.novamind.app.common.log.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 录音前台服务：持有 [AudioRecorder]，在锁屏 / 切后台时保持麦克风采集不中断，
 * 并在通知栏 / 锁屏常驻一条带「暂停-继续 / 停止」操作、点击可回到应用的通知。
 *
 * UI 通过 [start] / [pause] / [resume] / [stop] / [cancel] 下发指令；
 * 状态统一回写到 [RecordingController]，由 UI 观察。
 */
class RecordingService : Service() {

    // 达最大时长（30min）自动停止：回调在 MediaRecorder 线程触发，切回 service 主作用域收尾。
    private val recorder by lazy {
        AudioRecorder(this) { scope.launch { handleStop() } }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null

    private var elapsed = 0
    private var peak = 0
    private var paused = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_STOP -> handleStop()
            ACTION_CANCEL -> handleCancel()
        }
        // 录音不应在被系统杀死后凭空重启（无 intent 会得到 null action）。
        return START_NOT_STICKY
    }

    private fun handleStart() {
        if (RecordingController.state.value.active) return

        // 先起前台（满足 startForegroundService 5s 内必须 startForeground 的约束），
        // 再在 IO 线程做「空间预检 + 清理」，据结果决定真正开录或中止。
        createChannel()
        startInForeground()

        scope.launch {
            val ready = withContext(Dispatchers.IO) {
                RecordingCleaner.ensureSpaceForRecording(applicationContext)
            }
            if (!ready) {
                DebugLog.w(TAG, "storage low, abort recording")
                RecordingController.update { it.copy(active = false, cancelled = true) }
                finishService()
                return@launch
            }
            if (!recorder.start()) {
                // 启动失败（如权限被收回）：上报取消并退出。
                RecordingController.update { it.copy(active = false, cancelled = true) }
                finishService()
                return@launch
            }
            elapsed = 0
            peak = 0
            paused = false
            RecordingController.update { RecordingSnapshot(active = true, paused = false) }
            startLoop()
            updateNotification()
        }
    }

    private fun handlePause() {
        if (!RecordingController.state.value.active || paused) return
        paused = true
        recorder.pause()
        RecordingController.update { it.copy(paused = true) }
        updateNotification()
    }

    private fun handleResume() {
        if (!RecordingController.state.value.active || !paused) return
        paused = false
        recorder.resume()
        RecordingController.update { it.copy(paused = false) }
        updateNotification()
    }

    private fun handleStop() {
        // 重入保护：用户停止与 30min 自动停止可能并发，仅首次生效。
        if (!RecordingController.state.value.active) return
        loop?.cancel()
        val path = recorder.stop()
        val result = path?.let { RecordingResult(it, elapsed, peak) }
        RecordingController.update {
            it.copy(active = false, paused = false, result = result, cancelled = result == null)
        }
        finishService()
    }

    private fun handleCancel() {
        loop?.cancel()
        recorder.cancel()
        RecordingController.update { it.copy(active = false, paused = false, cancelled = true) }
        finishService()
    }

    /** 100ms 轮询：累计计时 + 读取振幅，回写 [RecordingController] 并按秒刷新通知。 */
    private fun startLoop() {
        loop?.cancel()
        loop = scope.launch {
            var accMillis = 0L
            var lastSec = -1
            while (isActive) {
                delay(TICK_MS)
                if (paused) continue
                accMillis += TICK_MS
                elapsed = (accMillis / 1000).toInt()
                val amp = recorder.maxAmplitude()
                if (amp > peak) peak = amp
                RecordingController.update {
                    it.copy(elapsedSeconds = elapsed, amplitude = amp, peakAmplitude = peak)
                }
                if (elapsed != lastSec) {
                    lastSec = elapsed
                    updateNotification()
                }
            }
        }
    }

    private fun startInForeground() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun finishService() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val openPi = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            PendingIntent.getActivity(this, REQ_OPEN, launch, piFlags())
        }

        val toggle = if (paused) {
            NotificationCompat.Action(R.drawable.ic_play, "继续", servicePi(ACTION_RESUME, REQ_RESUME))
        } else {
            NotificationCompat.Action(R.drawable.ic_pause, "暂停", servicePi(ACTION_PAUSE, REQ_PAUSE))
        }
        val stop = NotificationCompat.Action(R.drawable.ic_close, "停止", servicePi(ACTION_STOP, REQ_STOP))

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(if (paused) "录音已暂停" else "正在录音")
            .setContentText(formatTime(elapsed))
            .setOngoing(true)
            // 只在首次弹出 heads-up 悬浮横幅，逐秒刷新不再重复弹出
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            // 高优先级 → 触发顶部 heads-up 悬浮横幅（渠道已设为静音，不发声不振动）
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(0)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply { openPi?.let { setContentIntent(it) } }
            .addAction(toggle)
            .addAction(stop)
            .build()
    }

    private fun updateNotification() {
        if (!RecordingController.state.value.active) return
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification())
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        // IMPORTANCE_HIGH 才会弹出 heads-up 悬浮横幅；但关掉声音与振动，
        // 避免提示音被麦克风录入、也不打扰用户。
        val channel = NotificationChannel(
            CHANNEL_ID, "录音", NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "录音进行时的悬浮通知"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        mgr.createNotificationChannel(channel)
    }

    private fun servicePi(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, RecordingService::class.java).setAction(action)
        return PendingIntent.getService(this, requestCode, intent, piFlags())
    }

    private fun piFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return flags
    }

    override fun onDestroy() {
        loop?.cancel()
        super.onDestroy()
    }

    companion object {
        // 注：渠道创建后系统不允许再调高重要级，换新 ID 确保 IMPORTANCE_HIGH 生效
        private const val TAG = "RecordingService"
        private const val CHANNEL_ID = "recording_v2"
        private const val NOTIF_ID = 1001
        private const val TICK_MS = 100L

        private const val REQ_OPEN = 10
        private const val REQ_PAUSE = 11
        private const val REQ_RESUME = 12
        private const val REQ_STOP = 13

        const val ACTION_START = "com.novamind.app.recording.START"
        const val ACTION_PAUSE = "com.novamind.app.recording.PAUSE"
        const val ACTION_RESUME = "com.novamind.app.recording.RESUME"
        const val ACTION_STOP = "com.novamind.app.recording.STOP"
        const val ACTION_CANCEL = "com.novamind.app.recording.CANCEL"

        /** 开始录音（启动前台服务）。 */
        fun start(context: Context) = send(context, ACTION_START, foreground = true)
        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun stop(context: Context) = send(context, ACTION_STOP)
        fun cancel(context: Context) = send(context, ACTION_CANCEL)

        private fun send(context: Context, action: String, foreground: Boolean = false) {
            val intent = Intent(context, RecordingService::class.java).setAction(action)
            if (foreground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        private fun formatTime(totalSeconds: Int): String {
            val m = totalSeconds / 60
            val s = totalSeconds % 60
            return "$m:${s.toString().padStart(2, '0')}"
        }
    }
}
