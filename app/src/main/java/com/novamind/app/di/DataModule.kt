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
 * 数据层依赖（自 NovieApplication 手工 DI 逐步迁移而来）。
 *
 * [AppDatabase.getInstance] 本身是进程级单例，与 NovieApplication 中遗留的
 * `database` 懒字段取到的是同一实例，迁移期间两边共存安全。
 * Recording 仓库仍在 NovieApplication，后续按需迁入。
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
