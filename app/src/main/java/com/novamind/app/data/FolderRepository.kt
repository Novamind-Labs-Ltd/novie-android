package com.novamind.app.data

import kotlinx.coroutines.flow.Flow

/** 文件夹（持久化）领域模型。 */
data class StoredFolder(
    val id: String,
    val name: String,
    val colorHex: String?,
    val sortIndex: Int,
)

interface FolderRepository {
    /** 全部文件夹（实时 Flow，按排序位升序）。 */
    val folders: Flow<List<StoredFolder>>

    /** 新建文件夹（已存在同名则忽略）。 */
    suspend fun create(name: String, colorHex: String?)

    /** 重命名文件夹。 */
    suspend fun rename(oldName: String, newName: String)

    /** 删除文件夹。 */
    suspend fun delete(name: String)

    /** 修改文件夹颜色（文件夹不存在则创建）。 */
    suspend fun setColor(name: String, colorHex: String?)

    /** 按给定名称顺序更新排序位。 */
    suspend fun setOrder(orderedNames: List<String>)
}
