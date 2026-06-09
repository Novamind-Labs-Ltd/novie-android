package com.novamind.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    /** 实时监听全部笔记，按最后更新时间倒序 */
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    /** 新增或更新（根据主键 id 判断） */
    @Upsert
    suspend fun upsert(entity: NoteEntity)

    /** 按 id 查询单条 */
    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NoteEntity?

    /** 删除单条 */
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)
}
