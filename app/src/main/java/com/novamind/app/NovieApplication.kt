package com.novamind.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.novamind.app.common.audio.RecordingCleaner
import com.novamind.app.common.google.GoogleTokenProvider
import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.ApiConfig
import com.novamind.app.common.net.CommonHeaders
import com.novamind.app.common.push.PushChannels
import com.novamind.app.data.calendar.CalendarEventCache
import com.novamind.app.feature.calendar.CalendarBindingStore
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
class NovieApplication : Application(), ImageLoaderFactory {

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

        ApiConfig.init(this)               // API 环境选择，供 NetworkModule 读取
        SentryUtils.init(this)             // 须在 ApiConfig 之后（按环境上报）
        CommonHeaders.init(this)           // 缓存 HTTPS 公用头部
        PushChannels.ensureDefault(this)   // 预创建 FCM 通知渠道
        RecordingCleaner.scheduleOnIdle(this) // 空闲时回收录音，不阻塞启动
    }

    /** 全局 Coil ImageLoader：不透明图用 RGB_565，内存减半。 */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .allowRgb565(true)
            .build()
}
