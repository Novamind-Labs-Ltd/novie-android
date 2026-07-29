package com.novamind.app.data.db

import com.novamind.app.data.FolderRepository
import com.novamind.app.data.StoredFolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomFolderRepository(private val dao: FolderDao) : FolderRepository {

    override val folders: Flow<List<StoredFolder>> =
        dao.getAll().map { list ->
            list.map { StoredFolder(it.id, it.name, it.colorHex, it.sortIndex) }
        }

    override suspend fun upsert(id: String, name: String, colorHex: String?) {
        val trimmed = name.trim()
        if (id.isBlank()) return
        val existing = dao.getById(id)
        dao.upsert(
            FolderEntity(
                id = id,
                name = trimmed,
                colorHex = colorHex,
                sortIndex = existing?.sortIndex ?: (dao.maxSortIndex() ?: -1) + 1,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun rename(id: String, newName: String) {
        val to = newName.trim()
        if (to.isEmpty()) return
        dao.rename(id, to)
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    override suspend fun setColor(id: String, name: String, colorHex: String?) {
        val trimmed = name.trim()
        if (id.isBlank()) return
        if (dao.getById(id) != null) {
            dao.updateColor(id, colorHex)
        } else {
            val nextSort = (dao.maxSortIndex() ?: -1) + 1
            dao.upsert(
                FolderEntity(
                    id = id,
                    name = trimmed,
                    colorHex = colorHex,
                    sortIndex = nextSort,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun setOrder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> dao.updateSortIndex(id, index) }
    }
}
