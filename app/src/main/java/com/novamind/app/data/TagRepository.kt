package com.novamind.app.data

import kotlinx.coroutines.flow.Flow

/** 标签（持久化）领域模型。 */
data class StoredTag(
    val id: String,
    val name: String,
    val colorHex: String,
)

interface TagRepository {
    /** 全部标签（实时 Flow）。 */
    val tags: Flow<List<StoredTag>>

    /** 新建标签（已存在同名则忽略）。 */
    suspend fun create(name: String, colorHex: String)
}
