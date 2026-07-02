package com.novamind.app.di

import android.content.Context
import com.novamind.app.common.google.GoogleCalendarAuthManager
import com.novamind.app.common.google.GoogleCalendarAuthSource
import com.novamind.app.data.calendar.CalendarEventCache
import com.novamind.app.data.calendar.GoogleCalendarRepository
import com.novamind.app.data.calendar.GoogleCalendarRepositoryImpl
import com.novamind.app.data.tasks.GoogleTasksRepository
import com.novamind.app.data.tasks.GoogleTasksRepositoryImpl
import com.novamind.app.feature.calendar.CalendarBindingStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Calendar 模块依赖（自 NovieApplication 手工 DI 迁移而来）。
 *
 * 均为进程级单例：
 * - 两个仓库无状态（token 由 GoogleTokenProvider 注入），背后的 Retrofit/OkHttp 需复用；
 * - BindingStore / EventCache 基于 MMKV，须在 [com.novamind.app.NovieApplication.onCreate]
 *   的 `MMKV.initialize` 之后**惰性**创建——Hilt 注入 ViewModel 时机天然满足；
 *   Application 内如需引用请用 `Provider<T>`（见 NovieApplication.clearCalendarLocalSession）。
 */
@Module
@InstallIn(SingletonComponent::class)
object CalendarModule {

    @Provides
    @Singleton
    fun provideGoogleCalendarRepository(): GoogleCalendarRepository = GoogleCalendarRepositoryImpl()

    @Provides
    @Singleton
    fun provideGoogleTasksRepository(): GoogleTasksRepository = GoogleTasksRepositoryImpl()

    @Provides
    @Singleton
    fun provideCalendarBindingStore(): CalendarBindingStore = CalendarBindingStore()

    @Provides
    @Singleton
    fun provideCalendarEventCache(): CalendarEventCache = CalendarEventCache()

    /** 静默授权 + revoke 来源：持 application context，不泄漏 Activity。 */
    @Provides
    @Singleton
    fun provideGoogleCalendarAuthSource(
        @ApplicationContext context: Context,
    ): GoogleCalendarAuthSource = GoogleCalendarAuthManager(context)
}
