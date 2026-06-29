package com.novamind.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    /** 实时监听全部标签，按创建时间升序。 */
    @Query("SELECT * FROM tags ORDER BY createdAt ASC")
    fun getAll(): Flow<List<TagEntity>>

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
}
