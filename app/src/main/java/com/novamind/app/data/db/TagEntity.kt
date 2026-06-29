package com.novamind.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 用户标签（持久化）：名称唯一（忽略大小写由上层保证），含颜色。 */
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,          // 本地 UUID
    val name: String,
    /** 标签颜色 #RRGGBB */
    val colorHex: String,
    /** 手动排序位（越小越靠前） */
    val sortIndex: Int = 0,
    val createdAt: Long,
)
