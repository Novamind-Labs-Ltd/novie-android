package com.novamind.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RecordingEntity::class,   // 单文件（不分片）；原 recording_segments 表已移除
        FolderEntity::class,
        TagEntity::class,
    ],
    version = 10,
    exportSchema = false,   // 调试阶段：不导出 schema、不记录版本 JSON（上线前再开启并写迁移）
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recordingDao(): RecordingDao

    abstract fun folderDao(): FolderDao

    abstract fun tagDao(): TagDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "novie.db",
                )
                    .addMigrations(MIGRATION_8_9, MIGRATION_9_10)
                    // 调试阶段：schema 变更直接销毁重建，不写迁移
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }

        /** 文件夹身份切换为服务端 id；移除名称唯一索引，保留其它本地数据与旧颜色记录。 */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_folders_name")
            }
        }

        /** 移除旧的本地笔记缓存；录音继续以云端 noteId 关联，不再依赖本地 notes 外键。 */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `recordings_new` (
                        `id` TEXT NOT NULL,
                        `noteId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `path` TEXT NOT NULL,
                        `bytes` INTEGER NOT NULL,
                        `sha256` TEXT NOT NULL,
                        `uploadStatus` TEXT NOT NULL,
                        `fileId` TEXT,
                        `remoteUrl` TEXT,
                        PRIMARY KEY(`id`)
                    )""".trimIndent(),
                )
                db.execSQL(
                    """INSERT INTO `recordings_new`
                        (`id`, `noteId`, `createdAt`, `durationMs`, `path`, `bytes`, `sha256`, `uploadStatus`, `fileId`, `remoteUrl`)
                        SELECT `id`, `noteId`, `createdAt`, `durationMs`, `path`, `bytes`, `sha256`, `uploadStatus`, `fileId`, `remoteUrl`
                        FROM `recordings`""".trimIndent(),
                )
                db.execSQL("DROP TABLE `recordings`")
                db.execSQL("DROP TABLE IF EXISTS `notes`")
                db.execSQL("ALTER TABLE `recordings_new` RENAME TO `recordings`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recordings_noteId` ON `recordings` (`noteId`)")
            }
        }
    }
}
