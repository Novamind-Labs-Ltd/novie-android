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
}
