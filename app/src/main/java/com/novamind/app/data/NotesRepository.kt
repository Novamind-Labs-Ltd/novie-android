package com.novamind.app.data

import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.feature.create.model.RemoteNote
import com.novamind.app.feature.create.model.RemoteNotePage
import com.novamind.app.feature.create.model.UpdateNoteOutcome

/**
 * 云端笔记仓库（对接后端 /api/v1.0/notes）。对上只暴露领域模型与 [ApiResult] 三态，
 * DTO/网络细节不外泄（见 docs/data-layering-design.md）。
 *
 * content 约定：App 以 `{"body": <编辑器文档字符串>}` 作为笔记内容 JSON；调用方只传 title/body 文本。
 */
interface NotesRepository {

    /**
     * 笔记列表（GET，keyset 游标分页）。同一接口经参数切换「活跃 / 回收站 / 某文件夹内」三视图。
     *
     * @param trashed  false=活跃笔记；true=回收站；null=走后端默认（false）。
     * @param folderId 仅活跃视图有效：`<uuid>`=该文件夹内；`none`=未归档；null=不按文件夹过滤。
     * @param limit    每页条数，null=后端默认 50（硬上限 100）。
     * @param cursor   上一页返回的 nextCursor；null=第一页。
     */
    suspend fun listNotes(
        trashed: Boolean? = null,
        folderId: String? = null,
        limit: Int? = null,
        cursor: String? = null,
    ): ApiResult<RemoteNotePage>

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

    /**
     * 移入/移出回收站（PATCH `{trashed}`）。[trashed]=true 移入、false 恢复。
     * 返回变更后的 [RemoteNote]。
     */
    suspend fun setTrashed(id: String, trashed: Boolean): ApiResult<RemoteNote>

    /**
     * 永久删除（DELETE，两步制：须**已在回收站**）。成功无数据（HTTP 204）。
     * 仍是活跃笔记 → 业务错误 40906；转写进行中 → 40907（可重试）。
     */
    suspend fun deleteNote(id: String): ApiResult<Unit>
}
