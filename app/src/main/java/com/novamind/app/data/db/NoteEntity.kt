package com.novamind.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    /** JSON 数组，元素格式：{"id":"…","name":"…","colorHex":"…"} */
    val tagsJson: String,
    /** JSON 对象或 null，格式：{"id":"…","name":"…"} */
    val folderJson: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
