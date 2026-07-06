package com.novamind.app.data

import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.feature.create.model.RemoteNote
import com.novamind.app.feature.create.model.UpdateNoteOutcome

/**
 * 云端笔记仓库（对接后端 /api/v1.0/notes）。对上只暴露领域模型与 [ApiResult] 三态，
 * DTO/网络细节不外泄（见 docs/data-layering-design.md）。
 *
 * content 约定：App 以 `{"body": <编辑器文档字符串>}` 作为笔记内容 JSON；调用方只传 title/body 文本。
 */
interface NotesRepository {

    /** 创建笔记（POST）。成功返回带 rev 的 [RemoteNote]。 */
    suspend fun createNote(title: String?, body: String): ApiResult<RemoteNote>

    /** 获取单条笔记（GET）。 */
    suspend fun getNote(id: String): ApiResult<RemoteNote>

    /** 全量更新（PUT，latest-wins）。需传客户端持有的基准 [rev]；结果以 [UpdateNoteOutcome.applied] 判断。 */
    suspend fun updateNote(
        id: String,
        rev: Long,
        title: String?,
        body: String,
        schemaVersion: Int? = null,
    ): ApiResult<UpdateNoteOutcome>
}
