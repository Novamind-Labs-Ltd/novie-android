package com.novamind.app.data.db

import com.novamind.app.feature.create.model.Folder
import com.novamind.app.feature.create.model.Note
import com.novamind.app.feature.create.model.Tag
import org.json.JSONArray
import org.json.JSONObject

// ─── Tag JSON helpers ────────────────────────────────────────────────────────

internal fun List<Tag>.toJson(): String {
    val arr = JSONArray()
    forEach { tag ->
        arr.put(
            JSONObject().apply {
                put("id", tag.id)
                put("name", tag.name)
                put("colorHex", tag.colorHex)
            }
        )
    }
    return arr.toString()
}

internal fun String.toTagList(): List<Tag> {
    if (isBlank()) return emptyList()
    return try {
        val arr = JSONArray(this)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Tag(
                id = obj.getString("id"),
                name = obj.getString("name"),
                colorHex = obj.optString("colorHex", "#3D7A5A"),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

// ─── Folder JSON helpers ─────────────────────────────────────────────────────

internal fun Folder.toJson(): String =
    JSONObject().apply {
        put("id", id)
        put("name", name)
        put("iconEmoji", iconEmoji)
    }.toString()

internal fun String.toFolder(): Folder? = try {
    val obj = JSONObject(this)
    Folder(
        id = obj.getString("id"),
        name = obj.getString("name"),
        iconEmoji = obj.optString("iconEmoji", "📁"),
    )
} catch (_: Exception) {
    null
}

// ─── NoteEntity ↔ Note mappers ───────────────────────────────────────────────

internal fun NoteEntity.toNote() = Note(
    id = id,
    title = title,
    body = body,
    tags = tagsJson.toTagList(),
    folder = folderJson?.toFolder(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun Note.toEntity() = NoteEntity(
    id = id,
    title = title,
    body = body,
    tagsJson = tags.toJson(),
    folderJson = folder?.toJson(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)
