package com.novamind.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.novamind.app.common.audio.RecordingCleaner
import com.novamind.app.common.net.ApiConfig
import com.novamind.app.common.net.CommonHeaders
import com.novamind.app.common.push.PushChannels
import com.novamind.app.data.FolderRepository
import com.novamind.app.data.NoteRepository
import com.novamind.app.data.RecordingRepository
import com.novamind.app.data.RoomFolderRepository
import com.novamind.app.data.RoomNoteRepository
import com.novamind.app.data.RoomRecordingRepository
import com.novamind.app.data.RoomTagRepository
import com.novamind.app.data.TagRepository
import com.novamind.app.common.google.GoogleCalendarAuthManager
import com.novamind.app.common.google.GoogleCalendarAuthSource
import com.novamind.app.common.google.GoogleTokenProvider
import com.novamind.app.data.calendar.CalendarEventCache
import com.novamind.app.data.calendar.GoogleCalendarRepository
import com.novamind.app.data.calendar.GoogleCalendarRepositoryImpl
import com.novamind.app.data.db.AppDatabase
import com.novamind.app.data.tasks.GoogleTasksRepository
import com.novamind.app.data.tasks.GoogleTasksRepositoryImpl
import com.novamind.app.feature.calendar.CalendarBindingStore
import com.novamind.app.util.SentryUtils
import com.tencent.mmkv.MMKV

class NovieApplication : Application(), ImageLoaderFactory {

    val database by lazy { AppDatabase.getInstance(this) }
    val noteRepository: NoteRepository by lazy { RoomNoteRepository(database.noteDao()) }
    val folderRepository: FolderRepository by lazy { RoomFolderRepository(database.folderDao()) }
    val tagRepository: TagRepository by lazy { RoomTagRepository(database.tagDao()) }
    val recordingRepository: RecordingRepository by lazy {
        RoomRecordingRepository(database.recordingDao())
    }
    // Google 日历仓库（无状态、单例即可；token 由 GoogleTokenProvider 注入）
    val googleCalendarRepository: GoogleCalendarRepository by lazy { GoogleCalendarRepositoryImpl() }
    // Google 任务仓库（与日历共用同一 Google token，scope 含 tasks.readonly）
    val googleTasksRepository: GoogleTasksRepository by lazy { GoogleTasksRepositoryImpl() }
    // 日历绑定（连接标记 + 账号邮箱）与按账号隔离的事件缓存
    val calendarBindingStore: CalendarBindingStore by lazy { CalendarBindingStore() }
    val calendarEventCache: CalendarEventCache by lazy { CalendarEventCache() }
    // 静默授权 + revoke 来源（用 application context，不泄漏到 ViewModel）
    val googleCalendarAuthSource: GoogleCalendarAuthSource by lazy { GoogleCalendarAuthManager(this) }

    /**
     * 清除日历本地会话：删 token + 删绑定 + 清缓存（不 revoke Google 授权）。
     * 供 **App 退出登录**（Auth0）复用——退出登录 ≠ 取消授权，故不 revoke，
     * 重新登录可静默恢复；但需清本地，避免下一个登录用户看到上个账号的日历。
     */
    fun clearCalendarLocalSession() {
        GoogleTokenProvider.clear()
        calendarBindingStore.clear()
        calendarEventCache.clear()
    }

    override fun onCreate() {
        super.onCreate()
        // 键值存储初始化：必须最先调用，KeyValueStore/MmkvStore 依赖它
        MMKV.initialize(this)
        // 载入 API 域名（环境）选择，供 NetworkModule 读取
        ApiConfig.init(this)
        // 按环境初始化 Sentry（dev 不上传，st/prod 上传并打 environment 标签）；须在 ApiConfig.init 之后
        SentryUtils.init(this)
        // 构建并缓存 HTTPS 公用头部（平台/版本/设备标识等静态字段）
        CommonHeaders.init(this)
        // 预创建 FCM 默认通知渠道（后台推送到达时渠道须已存在）
        PushChannels.ensureDefault(this)
        // 启动后空闲时回收录音：可用空间过低时按最久优先删除，腾出空间（不阻塞启动）
        RecordingCleaner.scheduleOnIdle(this)
    }

    /**
     * 全局 Coil ImageLoader：允许不透明图用 RGB_565（内存减半）；
     * HARDWARE bitmap（API 26+）由 Coil 默认开启，bitmap 走显存、降低堆压力与 OOM 风险。
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .allowRgb565(true)
            .build()
}
