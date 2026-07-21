package com.novamind.app.data

import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.CreateNoteRequestDto
import com.novamind.app.common.net.NetworkModule
import com.novamind.app.common.net.NoteDto
import com.novamind.app.common.net.NoteListItemDto
import com.novamind.app.common.net.NotePageViewDto
import com.novamind.app.common.net.SetBorderColorRequestDto
import com.novamind.app.common.net.TrashNoteRequestDto
import com.novamind.app.common.net.UpdateNoteRequestDto
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.apiCall
import com.novamind.app.common.net.response.mapLogged
import com.novamind.app.feature.create.editor.NoteDocument
import com.novamind.app.feature.create.model.RemoteNote
import com.novamind.app.feature.create.model.RemoteNotePage
import com.novamind.app.feature.create.model.RemoteNoteSummary
import com.novamind.app.feature.create.model.UpdateNoteOutcome
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * [RemoteNoteRepository] 的网络实现：走统一响应 [apiCall]，把 DTO 映射为领域模型，DTO 不外泄。
 * 每个笔记能力都记日志（统一走 [AppLog]）：开始 / 成功 / 业务错误 / 网络错误，便于联调与线上排障。
 *
 * 「成功映射 + 三分支日志」的样板由 [mapLogged] 统一收尾——各方法只保留「开始」日志、发请求、给映射与文案。
 */
class RemoteNoteRepositoryImpl : RemoteNoteRepository {

    override suspend fun listNotes(
        trashed: Boolean?,
        folderId: String?,
        limit: Int?,
        cursor: String?,
    ): ApiResult<RemoteNotePage> {
        AppLog.i(TAG) { "listNotes 开始 trashed=$trashed folderId=$folderId limit=$limit hasCursor=${cursor != null}" }
        return apiCall {
            NetworkModule.notesApi.list(trashed = trashed, folderId = folderId, limit = limit, cursor = cursor)
        }.mapLogged(
            tag = TAG,
            op = "listNotes",
            transform = { it?.toDomain() ?: RemoteNotePage(emptyList(), null) },
            successLog = { "listNotes 成功 count=${it?.items?.size} hasNext=${it?.nextCursor != null}" },
        )
    }

    override suspend fun createNote(title: String?, body: String): ApiResult<RemoteNote> {
        AppLog.i(TAG) { "createNote 开始 titleLen=${title?.length ?: 0} bodyLen=${body.length}" }
        return apiCall {
            NetworkModule.notesApi.create(
                CreateNoteRequestDto(title = title, content = contentOf(body), preview = previewOf(body)),
            )
        }.mapLogged(
            tag = TAG,
            op = "createNote",
            transform = { it?.toDomain() },
            successLog = { "createNote 成功 id=${it?.id} rev=${it?.rev}" },
        )
    }

    override suspend fun getNote(id: String): ApiResult<RemoteNote> {
        AppLog.i(TAG) { "getNote 开始 id=$id" }
        return apiCall { NetworkModule.notesApi.get(id) }.mapLogged(
            tag = TAG,
            op = "getNote id=$id",
            transform = { it?.toDomain() },
            successLog = { "getNote 成功 id=$id rev=${it?.rev}" },
        )
    }

    override suspend fun updateNote(
        id: String,
        rev: Long,
        title: String?,
        body: String,
        schemaVersion: Int?,
    ): ApiResult<UpdateNoteOutcome> {
        AppLog.i(TAG) { "updateNote 开始 id=$id baseRev=$rev bodyLen=${body.length}" }
        return apiCall {
            NetworkModule.notesApi.update(
                id,
                UpdateNoteRequestDto(
                    rev = rev,
                    title = title,
                    content = contentOf(body),
                    schemaVersion = schemaVersion,
                    preview = previewOf(body),
                ),
            )
        }.mapLogged(
            tag = TAG,
            op = "updateNote id=$id",
            transform = { it?.let { d -> UpdateNoteOutcome(applied = d.applied, note = d.note?.toDomain()) } },
            successLog = { "updateNote 成功 id=$id applied=${it?.applied} newRev=${it?.note?.rev}" },
        )
    }

    override suspend fun setTrashed(id: String, trashed: Boolean): ApiResult<RemoteNote> {
        AppLog.i(TAG) { "setTrashed 开始 id=$id trashed=$trashed" }
        return apiCall {
            NetworkModule.notesApi.setTrashed(id, TrashNoteRequestDto(trashed = trashed))
        }.mapLogged(
            tag = TAG,
            op = "setTrashed id=$id",
            transform = { it?.toDomain() },
            successLog = { "setTrashed 成功 id=$id trashed=$trashed rev=${it?.rev}" },
        )
    }

    override suspend fun setFolder(id: String, folderId: String?): ApiResult<RemoteNote> {
        AppLog.i(TAG) { "setFolder 开始 id=$id folderId=$folderId" }
        // explicitNulls=false 会省略 null 字段,故用 JsonObject 显式传 folderId(null=移出未归档 / uuid=移入)
        val body = buildJsonObject {
            put("folderId", folderId?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        return apiCall {
            NetworkModule.notesApi.moveToFolder(id, body)
        }.mapLogged(
            tag = TAG,
            op = "setFolder id=$id",
            transform = { it?.toDomain() },
            successLog = { "setFolder 成功 id=$id folderId=$folderId rev=${it?.rev}" },
        )
    }

    override suspend fun deleteNote(id: String): ApiResult<Unit> {
        AppLog.i(TAG) { "deleteNote 开始 id=$id" }
        return apiCall { NetworkModule.notesApi.delete(id) }.mapLogged(
            tag = TAG,
            op = "deleteNote id=$id",
            transform = { Unit },   // 两步删除，成功无数据（HTTP 204）
            successLog = { "deleteNote 成功 id=$id" },
        )
    }

    override suspend fun setBorderColor(id: String, borderColorHex: String?): ApiResult<RemoteNote> {
        AppLog.i(TAG) { "setBorderColor 开始 id=$id hex=$borderColorHex" }
        return apiCall {
            NetworkModule.notesApi.setBorderColor(id, SetBorderColorRequestDto(borderColorHex = borderColorHex))
        }.mapLogged(
            tag = TAG,
            op = "setBorderColor id=$id",
            transform = { it?.toDomain() },
            successLog = { "setBorderColor 成功 id=$id rev=${it?.rev}" },
        )
    }

    // ── 映射 ──────────────────────────────────────────────────────────
    /** App 笔记内容约定：`{"body": <编辑器文档字符串>}`。 */
    private fun contentOf(body: String): JsonElement =
        JsonObject(mapOf("body" to JsonPrimitive(body)))

    /** 纯文本摘要：取正文首个非空行、最多 50 个字；空正文返回 null（不设摘要）。 */
    private fun previewOf(body: String): String? =
        NoteDocument.previewText(body)
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(PREVIEW_MAX_CHARS)

    private fun NotePageViewDto.toDomain(): RemoteNotePage = RemoteNotePage(
        items = items.map { it.toSummary() },
        nextCursor = nextCursor,
    )

    private fun NoteListItemDto.toSummary(): RemoteNoteSummary = RemoteNoteSummary(
        id = id,
        title = title,
        preview = preview,
        borderColorHex = borderColorHex,
        createdAt = createdAt,
        updatedAt = updatedAt,
        folderId = folderId,
        trashed = trashed,
        deletedAt = deletedAt,
        thumbnailUrl = thumbnailUrl,
    )

    private fun NoteDto.toDomain(): RemoteNote = RemoteNote(
        id = id,
        rev = rev,
        schemaVersion = schemaVersion ?: 1,
        title = title,
        content = content?.toString() ?: "{}",
        preview = preview,
        borderColorHex = borderColorHex,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private companion object {
        const val TAG = "NotesRepo"

        /** 摘要最大字数（前端截取；注意后端另有兜底截断，见 doc/frontend-api.md）。 */
        const val PREVIEW_MAX_CHARS = 50
    }
}
