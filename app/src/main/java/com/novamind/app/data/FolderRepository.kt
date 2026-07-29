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

    /** 按服务端文件夹 id 新建或更新本地展示信息。 */
    suspend fun upsert(id: String, name: String, colorHex: String?)

    /** 重命名文件夹。 */
    suspend fun rename(id: String, newName: String)

    /** 删除文件夹。 */
    suspend fun delete(id: String)

    /** 修改文件夹颜色（文件夹不存在则创建）。 */
    suspend fun setColor(id: String, name: String, colorHex: String?)

    /** 按给定服务端 id 顺序更新排序位。 */
    suspend fun setOrder(orderedIds: List<String>)
}
