package com.novamind.app.di

import android.content.Context
import com.novamind.app.data.FolderRepository
import com.novamind.app.data.NoteRepository
import com.novamind.app.data.RoomFolderRepository
import com.novamind.app.data.RoomNoteRepository
import com.novamind.app.data.RoomTagRepository
import com.novamind.app.data.TagRepository
import com.novamind.app.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据层依赖（自 NovieApplication 手工 DI 迁移而来）。
 * 数据库经 [AppDatabase.getInstance]（进程级单例）获取，各仓库均为无状态 DAO 包装，单例提供。
 * RecordingRepository 目前无消费方，未提供；接入录音数据时在此补充。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideNoteRepository(@ApplicationContext context: Context): NoteRepository =
        RoomNoteRepository(AppDatabase.getInstance(context).noteDao())

    @Provides
    @Singleton
    fun provideFolderRepository(@ApplicationContext context: Context): FolderRepository =
        RoomFolderRepository(AppDatabase.getInstance(context).folderDao())

    @Provides
    @Singleton
    fun provideTagRepository(@ApplicationContext context: Context): TagRepository =
        RoomTagRepository(AppDatabase.getInstance(context).tagDao())
}
