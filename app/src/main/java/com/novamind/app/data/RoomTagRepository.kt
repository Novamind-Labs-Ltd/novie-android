package com.novamind.app.data

import com.novamind.app.data.db.TagDao
import com.novamind.app.data.db.TagEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomTagRepository(private val dao: TagDao) : TagRepository {

    override val tags: Flow<List<StoredTag>> =
        dao.getAll().map { list ->
            list.map { StoredTag(it.id, it.name, it.colorHex) }
        }

    override suspend fun create(name: String, colorHex: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || dao.getByName(trimmed) != null) return
        dao.upsert(
            TagEntity(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                colorHex = colorHex,
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

    override suspend fun setColor(name: String, colorHex: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || colorHex.isBlank()) return
        dao.updateColor(trimmed, colorHex)
    }
}
