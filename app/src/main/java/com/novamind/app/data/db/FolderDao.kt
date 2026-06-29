package com.novamind.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    /** 实时监听全部文件夹，按排序位、创建时间升序。 */
    @Query("SELECT * FROM folders ORDER BY sortIndex ASC, createdAt ASC")
    fun getAll(): Flow<List<FolderEntity>>

    /** 按名称查询（忽略大小写）。 */
    @Query("SELECT * FROM folders WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): FolderEntity?

    /** 当前最大排序位（无数据时为 null）。 */
    @Query("SELECT MAX(sortIndex) FROM folders")
    suspend fun maxSortIndex(): Int?

    @Upsert
    suspend fun upsert(entity: FolderEntity)

    /** 重命名（忽略大小写匹配）。 */
    @Query("UPDATE folders SET name = :newName WHERE name = :oldName COLLATE NOCASE")
    suspend fun rename(oldName: String, newName: String)

    /** 更新排序位（忽略大小写匹配）。 */
    @Query("UPDATE folders SET sortIndex = :sortIndex WHERE name = :name COLLATE NOCASE")
    suspend fun updateSortIndex(name: String, sortIndex: Int)

    /** 按名称删除（忽略大小写）。 */
    @Query("DELETE FROM folders WHERE name = :name COLLATE NOCASE")
    suspend fun deleteByName(name: String)
}
