package com.novamind.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 笔记同步状态：本地新建 / 有未同步改动 / 同步中 / 已对齐 / 冲突。 */
enum class SyncStatus { LOCAL, DIRTY, SYNCING, SYNCED, CONFLICT }

@Entity(tableName = "notes", indices = [Index("syncStatus"), Index("deleted")])
data class NoteEntity(
    @PrimaryKey val id: String,          // 本地 UUID，离线即可生成，永不变
    val title: String,
    val body: String,
    /** JSON 数组，元素格式：{"id":"…","name":"…","colorHex":"…"} */
    val tagsJson: String,
    /** JSON 对象或 null，格式：{"id":"…","name":"…"} */
    val folderJson: String?,
    /** 自定义边框颜色 #RRGGBB；null = 默认边框 */
    val borderColorHex: String?,
    val createdAt: Long,
    val updatedAt: Long,                  // 内容最后修改时间（本地权威）

    // —— 离线同步相关 ——
    val serverId: String? = null,         // 后端 id；未上传过为 null
    val rev: Long = 0,                    // 服务端版本号，用于冲突判定
    val syncStatus: String = SyncStatus.LOCAL.name,
    val deleted: Boolean = false,         // tombstone：软删，同步后再物理清理
    val lastSyncedAt: Long? = null,       // 最近一次与后端对齐的时间
)
