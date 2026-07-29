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

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FolderEntity?

    /** 当前最大排序位（无数据时为 null）。 */
    @Query("SELECT MAX(sortIndex) FROM folders")
    suspend fun maxSortIndex(): Int?

    @Upsert
    suspend fun upsert(entity: FolderEntity)

    @Query("UPDATE folders SET name = :newName WHERE id = :id")
    suspend fun rename(id: String, newName: String)

    @Query("UPDATE folders SET sortIndex = :sortIndex WHERE id = :id")
    suspend fun updateSortIndex(id: String, sortIndex: Int)

    @Query("UPDATE folders SET colorHex = :colorHex WHERE id = :id")
    suspend fun updateColor(id: String, colorHex: String?)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: String)
}
