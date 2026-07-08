package com.novamind.app

import android.app.Application
import android.os.StatFs
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.novamind.app.common.audio.RecordingCleaner
import com.novamind.app.common.audio.RecordingUploadScheduler
import com.novamind.app.common.sync.NoteSyncScheduler
import com.novamind.app.common.google.GoogleTokenProvider
import com.novamind.app.common.config.AppConfig
import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.ApiConfig
import com.novamind.app.common.net.CommonHeaders
import com.novamind.app.common.push.PushChannels
import com.novamind.app.common.session.UserSessionManager
import com.novamind.app.data.calendar.CalendarEventCache
import com.novamind.app.feature.calendar.CalendarBindingStore
import com.novamind.app.ui.theme.FontStore
import com.novamind.app.ui.theme.ThemeStore
import com.novamind.app.util.SentryUtils
import com.tencent.mmkv.MMKV
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider

/**
 * 应用入口（Hilt 组合根）：只做进程级初始化、全局 Coil ImageLoader 和日历会话清理。
 * 业务依赖一律经 Hilt 提供（见 di/ 下各 Module），不在此手工构造。
 */
@HiltAndroidApp
class NovieApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    // WorkManager 的 Hilt Worker 工厂：使 @HiltWorker 可被注入依赖（on-demand 初始化）。
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    // Provider 惰性取用：字段注入早于 MMKV.initialize，get() 推迟到调用时，规避初始化顺序问题
    @Inject lateinit var calendarBindingStore: Provider<CalendarBindingStore>
    @Inject lateinit var calendarEventCache: Provider<CalendarEventCache>

    /**
     * 清除日历本地会话（删 token + 绑定 + 缓存），供退出登录复用。
     * 不 revoke Google 授权：退出 ≠ 取消授权，重新登录可静默恢复。
     */
    fun clearCalendarLocalSession() {
        GoogleTokenProvider.clear()
        calendarBindingStore.get().clear()
        calendarEventCache.get().clear()
    }

    /** 进程级初始化，顺序敏感。 */
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)                  // 日志管道最先起，后续初始化即可记日志
        MMKV.initialize(this)              // 键值存储
        FontStore.load()                   // 载入持久化的字体选择（须在 MMKV 之后）
        ThemeStore.load()                  // 载入持久化的主题模式（须在 MMKV 之后）
        UserSessionManager.loadCached()    // 冷启动即有：读缓存档案，UI 先渲染（须在 MMKV 之后）

        ApiConfig.init(this)               // API 环境选择，供 NetworkModule 读取
        SentryUtils.init(this)             // 须在 ApiConfig 之后（按环境上报）
        CommonHeaders.init(this)           // 缓存 HTTPS 公用头部
        PushChannels.ensureDefault(this)   // 预创建 FCM 通知渠道
        RecordingCleaner.scheduleOnIdle(this) // 空闲时回收录音，不阻塞启动
        RecordingUploadScheduler.resumeOnIdle(this) // 空闲时把未完成上传的录音重新入队（断点续传）

        NoteSyncScheduler.init(this)          // 存 app context，供仓库层无参触发同步
        NoteSyncScheduler.schedulePeriodic(this) // 周期兜底同步（15min，带网络约束）
        NoteSyncScheduler.requestSync(this)   // 启动时先推一次未同步笔记
    }

    /**
     * 全局 Coil ImageLoader：
     * - 不透明图用 RGB_565，内存减半。
     * - 磁盘缓存目录沿用 Coil 默认的 cacheDir/image_cache。
     * - 容量取「磁盘**可用**空间的 30%」，上限 1 GiB、下限 10 MiB。
     *   注意：Coil 自带的 maxSizePercent 是按磁盘**总**容量算的，这里按可用空间自行计算，
     *   故用 maxSizeBytes 传固定值（启动时快照一次）。
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .allowRgb565(true)
            .diskCache {
                val dir = cacheDir.resolve("image_cache")
                val availableBytes = StatFs(cacheDir.absolutePath).availableBytes
                val maxSize = (availableBytes * AppConfig.Media.IMAGE_DISK_CACHE_PERCENT).toLong()
                    .coerceIn(
                        AppConfig.Media.IMAGE_DISK_CACHE_MIN_BYTES,
                        AppConfig.Media.IMAGE_DISK_CACHE_MAX_BYTES,
                    )
                DiskCache.Builder()
                    .directory(dir)
                    .maxSizeBytes(maxSize)
                    .build()
            }
            .build()
}
