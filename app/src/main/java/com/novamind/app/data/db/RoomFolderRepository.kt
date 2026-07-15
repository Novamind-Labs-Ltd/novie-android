package com.novamind.app.data.db

import com.novamind.app.data.FolderRepository
import com.novamind.app.data.StoredFolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomFolderRepository(private val dao: FolderDao) : FolderRepository {

    override val folders: Flow<List<StoredFolder>> =
        dao.getAll().map { list ->
            list.map { StoredFolder(it.id, it.name, it.colorHex, it.sortIndex) }
        }

    override suspend fun create(name: String, colorHex: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || dao.getByName(trimmed) != null) return
        val nextSort = (dao.maxSortIndex() ?: -1) + 1
        dao.upsert(
            FolderEntity(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                colorHex = colorHex,
                sortIndex = nextSort,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun rename(oldName: String, newName: String) {
        val to = newName.trim()
        if (to.isEmpty()) return
        dao.rename(oldName.trim(), to)
    }

    override suspend fun delete(name: String) {
        dao.deleteByName(name.trim())
    }

    override suspend fun setColor(name: String, colorHex: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (dao.getByName(trimmed) != null) {
            dao.updateColor(trimmed, colorHex)
        } else {
            // 笔记派生但尚未入库的文件夹：登记并带上颜色
            val nextSort = (dao.maxSortIndex() ?: -1) + 1
            dao.upsert(
                FolderEntity(
                    id = UUID.randomUUID().toString(),
                    name = trimmed,
                    colorHex = colorHex,
                    sortIndex = nextSort,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun setOrder(orderedNames: List<String>) {
        orderedNames.forEachIndexed { index, name -> dao.updateSortIndex(name, index) }
    }
}
