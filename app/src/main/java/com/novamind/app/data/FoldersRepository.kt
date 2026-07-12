package com.novamind.app.data

import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.feature.create.folder.RemoteFolder
import com.novamind.app.feature.create.folder.RemoteFolderPage

/**
 * 云端文件夹仓库（对接后端 /api/v1.0/folders）。对上只暴露领域模型与 [ApiResult] 三态，
 * DTO/网络细节不外泄（见 docs/data-layering-design.md）。与本地 [FolderRepository]（Room）区分。
 */
interface FoldersRepository {

    /** 文件夹列表（GET，keyset 游标分页，仅活跃文件夹）。[limit] 缺省 50、上限 100；[cursor] 续页游标。 */
    suspend fun listFolders(limit: Int? = null, cursor: String? = null): ApiResult<RemoteFolderPage>

    /** 新建文件夹（POST）。成功返回带 id/sortOrder 的 [RemoteFolder]。 */
    suspend fun createFolder(name: String?): ApiResult<RemoteFolder>

    /** 重命名（PATCH `{name}`）。返回变更后的 [RemoteFolder]。 */
    suspend fun renameFolder(id: String, name: String): ApiResult<RemoteFolder>

    /** 移入/移出回收站（PATCH `{trashed}`）。true=移入、false=恢复。返回变更后的 [RemoteFolder]。 */
    suspend fun setTrashed(id: String, trashed: Boolean): ApiResult<RemoteFolder>

    /**
     * 拖拽重排序（PUT /folders/order）：按 [orderedIds] 顺序重写活跃文件夹的 sortOrder。成功无数据（HTTP 204）。
     * 含重复/未知/非本人/已回收 id → 业务错误。
     */
    suspend fun reorderFolders(orderedIds: List<String>): ApiResult<Unit>
}
