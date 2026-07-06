package com.novamind.app.data

import com.novamind.app.common.net.CreateNoteRequestDto
import com.novamind.app.common.net.NetworkModule
import com.novamind.app.common.net.NoteDto
import com.novamind.app.common.net.UpdateNoteRequestDto
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.apiCall
import com.novamind.app.feature.create.model.RemoteNote
import com.novamind.app.feature.create.model.UpdateNoteOutcome
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * [NotesRepository] 的网络实现：走统一响应 [apiCall]，把 DTO 映射为领域模型，DTO 不外泄。
 */
class RemoteNotesRepository : NotesRepository {

    override suspend fun createNote(title: String?, body: String): ApiResult<RemoteNote> =
        when (val r = apiCall {
            NetworkModule.notesApi.create(CreateNoteRequestDto(title = title, content = contentOf(body)))
        }) {
            is ApiResult.Success -> ApiResult.Success<RemoteNote>(r.data?.toDomain())
            is ApiResult.BizError -> r
            is ApiResult.NetworkError -> r
        }

    override suspend fun getNote(id: String): ApiResult<RemoteNote> =
        when (val r = apiCall { NetworkModule.notesApi.get(id) }) {
            is ApiResult.Success -> ApiResult.Success<RemoteNote>(r.data?.toDomain())
            is ApiResult.BizError -> r
            is ApiResult.NetworkError -> r
        }

    override suspend fun updateNote(
        id: String,
        rev: Long,
        title: String?,
        body: String,
        schemaVersion: Int?,
    ): ApiResult<UpdateNoteOutcome> =
        when (val r = apiCall {
            NetworkModule.notesApi.update(
                id,
                UpdateNoteRequestDto(rev = rev, title = title, content = contentOf(body), schemaVersion = schemaVersion),
            )
        }) {
            is ApiResult.Success -> ApiResult.Success<UpdateNoteOutcome>(
                r.data?.let { UpdateNoteOutcome(applied = it.applied, note = it.note?.toDomain()) },
            )
            is ApiResult.BizError -> r
            is ApiResult.NetworkError -> r
        }

    // ── 映射 ──────────────────────────────────────────────────────────
    /** App 笔记内容约定：`{"body": <编辑器文档字符串>}`。 */
    private fun contentOf(body: String): JsonElement =
        JsonObject(mapOf("body" to JsonPrimitive(body)))

    private fun NoteDto.toDomain(): RemoteNote = RemoteNote(
        id = id,
        rev = rev,
        schemaVersion = schemaVersion ?: 1,
        title = title,
        content = content?.toString() ?: "{}",
        borderColorHex = borderColorHex,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
