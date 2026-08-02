package com.novamind.app.di

import android.content.Context
import com.novamind.app.data.FolderRepository
import com.novamind.app.data.FoldersRepository
import com.novamind.app.data.RemoteNoteRepository
import com.novamind.app.data.RecordingRepository
import com.novamind.app.data.RemoteFoldersRepository
import com.novamind.app.data.RemoteNoteRepositoryImpl
import com.novamind.app.data.TagRepository
import com.novamind.app.data.db.AppDatabase
import com.novamind.app.data.db.RoomFolderRepository
import com.novamind.app.data.db.RoomRecordingRepository
import com.novamind.app.data.db.RoomTagRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据层依赖（自 NovieApplication 手工 DI 迁移而来）。
 * 数据库经 [AppDatabase.getInstance]（进程级单例）获取，各仓库均为无状态 DAO 包装，单例提供。
 * 本地 Room 仅保留录音上传状态、文件夹颜色与标签；笔记正文与元数据以服务端为准。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideFolderRepository(@ApplicationContext context: Context): FolderRepository =
        RoomFolderRepository(AppDatabase.getInstance(context).folderDao())

    @Provides
    @Singleton
    fun provideTagRepository(@ApplicationContext context: Context): TagRepository =
        RoomTagRepository(AppDatabase.getInstance(context).tagDao())

    @Provides
    @Singleton
    fun provideRecordingRepository(@ApplicationContext context: Context): RecordingRepository =
        RoomRecordingRepository(AppDatabase.getInstance(context).recordingDao())

    /** 云端笔记仓库（无状态，包装 NetworkModule）。 */
    @Provides
    @Singleton
    fun provideRemoteNoteRepository(): RemoteNoteRepository = RemoteNoteRepositoryImpl()

    /** 云端文件夹仓库（无状态，包装 NetworkModule）。 */
    @Provides
    @Singleton
    fun provideFoldersRepository(): FoldersRepository = RemoteFoldersRepository()
}
