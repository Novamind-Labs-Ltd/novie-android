package com.novamind.app.common.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.novamind.app.common.log.AppLog
import java.util.concurrent.TimeUnit

/**
 * 笔记同步调度（离线优先，见 docs/note-offline-sync-design.md §7）。
 *
 * 触发时机：编辑后 [requestSync]（合并短时间内多次请求）、周期兜底 [schedulePeriodic]、App 启动。
 * 全部带 `NetworkType.CONNECTED` 约束 + 指数退避，断网/被杀由 WorkManager 续上。
 *
 * 在 `Application.onCreate` 调用 [init] 存下 app context，之后仓库层可无参调用 [requestSync]
 * （与「本地写入后仓库触发同步」的设计一致，无需把 Context 层层透传）。
 */
object NoteSyncScheduler {

    private const val UNIQUE_ONE_TIME = "note-sync"
    private const val UNIQUE_PERIODIC = "note-sync-periodic"
    private const val PERIOD_MINUTES = 15L
    private const val TAG = "NoteSync"

    @Volatile
    private var appContext: Context? = null

    /** 在 Application.onCreate 调用一次，存下 app context。 */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val networkConstraint: Constraints
        get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** 立即请求一次同步（唯一任务，已排队则合并保留）。context 缺省用 [init] 存下的。 */
    fun requestSync(context: Context? = null) {
        val ctx = context?.applicationContext ?: appContext ?: run {
            AppLog.w(TAG) { "requestSync 前未 init()，忽略" }
            return
        }
        val request = OneTimeWorkRequestBuilder<NoteSyncWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniqueWork(UNIQUE_ONE_TIME, ExistingWorkPolicy.KEEP, request)
    }

    /** 周期兜底同步（默认 15 分钟）。 */
    fun schedulePeriodic(context: Context? = null) {
        val ctx = context?.applicationContext ?: appContext ?: return
        val request = PeriodicWorkRequestBuilder<NoteSyncWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
