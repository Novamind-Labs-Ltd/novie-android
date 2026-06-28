package com.novamind.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    /** 实时监听可见笔记（排除已软删的 tombstone），按最后更新时间倒序 */
    @Query("SELECT * FROM notes WHERE deleted = 0 ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    /** 实时监听回收站笔记（已软删的 tombstone），按删除时间（updatedAt）倒序 */
    @Query("SELECT * FROM notes WHERE deleted = 1 ORDER BY updatedAt DESC")
    fun getDeletedNotes(): Flow<List<NoteEntity>>

    /** 从回收站恢复：清除 tombstone 并标记待同步。 */
    @Query("UPDATE notes SET deleted = 0, syncStatus = 'DIRTY', updatedAt = :timestamp WHERE id = :id")
    suspend fun restore(id: String, timestamp: Long)

    /** 待同步（本地新建或有未同步改动）的笔记，供上行同步使用。 */
    @Query("SELECT * FROM notes WHERE syncStatus IN ('LOCAL', 'DIRTY')")
    suspend fun dirtyNotes(): List<NoteEntity>

    /** 软删：打 tombstone 并标记待同步（保留行，待同步完成后再物理删除）。 */
    @Query("UPDATE notes SET deleted = 1, syncStatus = 'DIRTY', updatedAt = :timestamp WHERE id = :id")
    suspend fun markDeleted(id: String, timestamp: Long)

    /** 同步成功后回写后端 id / 版本，并置为已对齐。 */
    @Query(
        "UPDATE notes SET serverId = :serverId, rev = :rev, " +
            "syncStatus = 'SYNCED', lastSyncedAt = :timestamp WHERE id = :id",
    )
    suspend fun markSynced(id: String, serverId: String, rev: Long, timestamp: Long)

    /** 新增或更新（根据主键 id 判断） */
    @Upsert
    suspend fun upsert(entity: NoteEntity)

    /** 按 id 查询单条 */
    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NoteEntity?

    /** 删除单条 */
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 清空全部（Debug 工具用） */
    @Query("DELETE FROM notes")
    suspend fun clearAll()

    /** 笔记总数 */
    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int
}
