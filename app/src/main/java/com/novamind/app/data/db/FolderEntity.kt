package com.novamind.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 用户文件夹的本地展示信息；主键与服务端 folder id 一致，名称允许为空或重复。 */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** 文件夹颜色 #RRGGBB；null = 默认 */
    val colorHex: String?,
    /** 手动排序位（越小越靠前） */
    val sortIndex: Int,
    val createdAt: Long,
)
