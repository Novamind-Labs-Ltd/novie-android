package com.novamind.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    /** 实时监听全部标签，按排序位、创建时间升序。 */
    @Query("SELECT * FROM tags ORDER BY sortIndex ASC, createdAt ASC")
    fun getAll(): Flow<List<TagEntity>>

    /** 当前最大排序位（无数据时为 null）。 */
    @Query("SELECT MAX(sortIndex) FROM tags")
    suspend fun maxSortIndex(): Int?

    /** 更新排序位（忽略大小写匹配）。 */
    @Query("UPDATE tags SET sortIndex = :sortIndex WHERE name = :name COLLATE NOCASE")
    suspend fun updateSortIndex(name: String, sortIndex: Int)

    /** 按名称查询（忽略大小写）。 */
    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): TagEntity?

    @Upsert
    suspend fun upsert(entity: TagEntity)

    /** 重命名（忽略大小写匹配）。 */
    @Query("UPDATE tags SET name = :newName WHERE name = :oldName COLLATE NOCASE")
    suspend fun rename(oldName: String, newName: String)

    /** 按名称删除（忽略大小写）。 */
    @Query("DELETE FROM tags WHERE name = :name COLLATE NOCASE")
    suspend fun deleteByName(name: String)

    /** 更新颜色（忽略大小写匹配）。 */
    @Query("UPDATE tags SET colorHex = :colorHex WHERE name = :name COLLATE NOCASE")
    suspend fun updateColor(name: String, colorHex: String)
}
