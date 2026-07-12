package com.novamind.app.data

import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.CreateNoteRequestDto
import com.novamind.app.common.net.NetworkModule
import com.novamind.app.common.net.NoteDto
import com.novamind.app.common.net.NoteListItemDto
import com.novamind.app.common.net.NotePageViewDto
import com.novamind.app.common.net.UpdateNoteRequestDto
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.apiCall
import com.novamind.app.feature.create.model.RemoteNote
import com.novamind.app.feature.create.model.RemoteNotePage
import com.novamind.app.feature.create.model.RemoteNoteSummary
import com.novamind.app.feature.create.model.UpdateNoteOutcome
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * [NotesRepository] 的网络实现：走统一响应 [apiCall]，把 DTO 映射为领域模型，DTO 不外泄。
 * 每个笔记能力都打日志（统一走 [AppLog]）：开始/成功/业务错误/网络错误，便于联调与线上排障。
 */
class RemoteNotesRepository : NotesRepository {

    override suspend fun listNotes(
        trashed: Boolean?,
        folderId: String?,
        limit: Int?,
        cursor: String?,
    ): ApiResult<RemoteNotePage> {
        AppLog.i(TAG) { "listNotes 开始 trashed=$trashed folderId=$folderId limit=$limit hasCursor=${cursor != null}" }
        return when (val r = apiCall {
            NetworkModule.notesApi.list(trashed = trashed, folderId = folderId, limit = limit, cursor = cursor)
        }) {
            is ApiResult.Success -> {
                val page = r.data?.toDomain() ?: RemoteNotePage(emptyList(), null)
                AppLog.i(TAG) { "listNotes 成功 count=${page.items.size} hasNext=${page.nextCursor != null}" }
                ApiResult.Success<RemoteNotePage>(page)
            }
            is ApiResult.BizError -> {
                AppLog.w(TAG) { "listNotes 业务错误 code=${r.code} traceId=${r.traceId} msg=${r.message}" }
                r
            }
            is ApiResult.NetworkError -> {
                AppLog.w(TAG) { "listNotes 网络错误: ${r.message}" }
                r
            }
        }
    }

    override suspend fun createNote(title: String?, body: String): ApiResult<RemoteNote> {
        AppLog.i(TAG) { "createNote 开始 titleLen=${title?.length ?: 0} bodyLen=${body.length}" }
        return when (val r = apiCall {
            NetworkModule.notesApi.create(CreateNoteRequestDto(title = title, content = contentOf(body)))
        }) {
            is ApiResult.Success -> {
                val note = r.data?.toDomain()
                AppLog.i(TAG) { "createNote 成功 id=${note?.id} rev=${note?.rev}" }
                ApiResult.Success<RemoteNote>(note)
            }
            is ApiResult.BizError -> {
                AppLog.w(TAG) { "createNote 业务错误 code=${r.code} traceId=${r.traceId} msg=${r.message}" }
                r
            }
            is ApiResult.NetworkError -> {
                AppLog.w(TAG) { "createNote 网络错误: ${r.message}" }
                r
            }
        }
    }

    override suspend fun getNote(id: String): ApiResult<RemoteNote> {
        AppLog.i(TAG) { "getNote 开始 id=$id" }
        return when (val r = apiCall { NetworkModule.notesApi.get(id) }) {
            is ApiResult.Success -> {
                val note = r.data?.toDomain()
                AppLog.i(TAG) { "getNote 成功 id=$id rev=${note?.rev}" }
                ApiResult.Success<RemoteNote>(note)
            }
            is ApiResult.BizError -> {
                AppLog.w(TAG) { "getNote 业务错误 id=$id code=${r.code} traceId=${r.traceId} msg=${r.message}" }
                r
            }
            is ApiResult.NetworkError -> {
                AppLog.w(TAG) { "getNote 网络错误 id=$id: ${r.message}" }
                r
            }
        }
    }

    override suspend fun updateNote(
        id: String,
        rev: Long,
        title: String?,
        body: String,
        schemaVersion: Int?,
    ): ApiResult<UpdateNoteOutcome> {
        AppLog.i(TAG) { "updateNote 开始 id=$id baseRev=$rev bodyLen=${body.length}" }
        return when (val r = apiCall {
            NetworkModule.notesApi.update(
                id,
                UpdateNoteRequestDto(rev = rev, title = title, content = contentOf(body), schemaVersion = schemaVersion),
            )
        }) {
            is ApiResult.Success -> {
                val outcome = r.data?.let { UpdateNoteOutcome(applied = it.applied, note = it.note?.toDomain()) }
                AppLog.i(TAG) { "updateNote 成功 id=$id applied=${outcome?.applied} newRev=${outcome?.note?.rev}" }
                ApiResult.Success<UpdateNoteOutcome>(outcome)
            }
            is ApiResult.BizError -> {
                AppLog.w(TAG) { "updateNote 业务错误 id=$id code=${r.code} traceId=${r.traceId} msg=${r.message}" }
                r
            }
            is ApiResult.NetworkError -> {
                AppLog.w(TAG) { "updateNote 网络错误 id=$id: ${r.message}" }
                r
            }
        }
    }

    // ── 映射 ──────────────────────────────────────────────────────────
    /** App 笔记内容约定：`{"body": <编辑器文档字符串>}`。 */
    private fun contentOf(body: String): JsonElement =
        JsonObject(mapOf("body" to JsonPrimitive(body)))

    private fun NotePageViewDto.toDomain(): RemoteNotePage = RemoteNotePage(
        items = items.map { it.toSummary() },
        nextCursor = nextCursor,
    )

    private fun NoteListItemDto.toSummary(): RemoteNoteSummary = RemoteNoteSummary(
        id = id,
        title = title,
        borderColorHex = borderColorHex,
        createdAt = createdAt,
        updatedAt = updatedAt,
        folderId = folderId,
        trashed = trashed,
        deletedAt = deletedAt,
    )

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

    private companion object {
        const val TAG = "NotesRepo"
    }
}
