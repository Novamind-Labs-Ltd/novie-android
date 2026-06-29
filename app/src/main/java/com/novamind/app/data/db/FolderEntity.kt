package com.novamind.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 用户文件夹（持久化）：名称唯一（忽略大小写由上层保证），含颜色与排序位。 */
@Entity(tableName = "folders", indices = [Index(value = ["name"], unique = true)])
data class FolderEntity(
    @PrimaryKey val id: String,          // 本地 UUID
    val name: String,
    /** 文件夹颜色 #RRGGBB；null = 默认 */
    val colorHex: String?,
    /** 手动排序位（越小越靠前） */
    val sortIndex: Int,
    val createdAt: Long,
)
