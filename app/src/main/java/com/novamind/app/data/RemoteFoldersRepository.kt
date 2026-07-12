package com.novamind.app.data

import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.CreateFolderRequestDto
import com.novamind.app.common.net.FolderDto
import com.novamind.app.common.net.FolderPageViewDto
import com.novamind.app.common.net.NetworkModule
import com.novamind.app.common.net.PatchFolderRequestDto
import com.novamind.app.common.net.ReorderFoldersRequestDto
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.apiCall
import com.novamind.app.feature.create.folder.RemoteFolder
import com.novamind.app.feature.create.folder.RemoteFolderPage

/**
 * [FoldersRepository] 的网络实现：走统一响应 [apiCall]，把 DTO 映射为领域模型，DTO 不外泄。
 * 每个能力都打日志（开始/成功/业务错误/网络错误），便于联调与线上排障。
 */
class RemoteFoldersRepository : FoldersRepository {

    override suspend fun listFolders(limit: Int?, cursor: String?): ApiResult<RemoteFolderPage> {
        AppLog.i(TAG) { "listFolders 开始 limit=$limit hasCursor=${cursor != null}" }
        return when (val r = apiCall { NetworkModule.foldersApi.list(limit = limit, cursor = cursor) }) {
            is ApiResult.Success -> {
                val page = r.data?.toDomain() ?: RemoteFolderPage(emptyList(), null)
                AppLog.i(TAG) { "listFolders 成功 count=${page.items.size} hasNext=${page.nextCursor != null}" }
                ApiResult.Success<RemoteFolderPage>(page)
            }
            is ApiResult.BizError -> {
                AppLog.w(TAG) { "listFolders 业务错误 code=${r.code} traceId=${r.traceId} msg=${r.message}" }
                r
            }
            is ApiResult.NetworkError -> {
                AppLog.w(TAG) { "listFolders 网络错误: ${r.message}" }
                r
            }
        }
    }

    override suspend fun createFolder(name: String?): ApiResult<RemoteFolder> {
        AppLog.i(TAG) { "createFolder 开始 nameLen=${name?.length ?: 0}" }
        return when (val r = apiCall {
            NetworkModule.foldersApi.create(CreateFolderRequestDto(name = name))
        }) {
            is ApiResult.Success -> {
                val folder = r.data?.toDomain()
                AppLog.i(TAG) { "createFolder 成功 id=${folder?.id}" }
                ApiResult.Success<RemoteFolder>(folder)
            }
            is ApiResult.BizError -> {
                AppLog.w(TAG) { "createFolder 业务错误 code=${r.code} traceId=${r.traceId} msg=${r.message}" }
                r
            }
            is ApiResult.NetworkError -> {
                AppLog.w(TAG) { "createFolder 网络错误: ${r.message}" }
                r
            }
        }
    }

    override suspend fun renameFolder(id: String, name: String): ApiResult<RemoteFolder> =
        patch(id, PatchFolderRequestDto(name = name), op = "renameFolder")

    override suspend fun setTrashed(id: String, trashed: Boolean): ApiResult<RemoteFolder> =
        patch(id, PatchFolderRequestDto(trashed = trashed), op = "setTrashed")

    override suspend fun reorderFolders(orderedIds: List<String>): ApiResult<Unit> {
        AppLog.i(TAG) { "reorderFolders 开始 count=${orderedIds.size}" }
        return when (val r = apiCall {
            NetworkModule.foldersApi.reorder(ReorderFoldersRequestDto(orderedIds = orderedIds))
        }) {
            is ApiResult.Success -> {
                AppLog.i(TAG) { "reorderFolders 成功 count=${orderedIds.size}" }
                ApiResult.Success<Unit>(Unit)
            }
            is ApiResult.BizError -> {
                AppLog.w(TAG) { "reorderFolders 业务错误 code=${r.code} traceId=${r.traceId} msg=${r.message}" }
                r
            }
            is ApiResult.NetworkError -> {
                AppLog.w(TAG) { "reorderFolders 网络错误: ${r.message}" }
                r
            }
        }
    }

    private suspend fun patch(id: String, body: PatchFolderRequestDto, op: String): ApiResult<RemoteFolder> {
        AppLog.i(TAG) { "$op 开始 id=$id" }
        return when (val r = apiCall { NetworkModule.foldersApi.patch(id, body) }) {
            is ApiResult.Success -> {
                val folder = r.data?.toDomain()
                AppLog.i(TAG) { "$op 成功 id=$id sortOrder=${folder?.sortOrder}" }
                ApiResult.Success<RemoteFolder>(folder)
            }
            is ApiResult.BizError -> {
                AppLog.w(TAG) { "$op 业务错误 id=$id code=${r.code} traceId=${r.traceId} msg=${r.message}" }
                r
            }
            is ApiResult.NetworkError -> {
                AppLog.w(TAG) { "$op 网络错误 id=$id: ${r.message}" }
                r
            }
        }
    }

    // ── 映射 ──────────────────────────────────────────────────────────
    private fun FolderPageViewDto.toDomain(): RemoteFolderPage = RemoteFolderPage(
        items = items.map { it.toDomain() },
        nextCursor = nextCursor,
    )

    private fun FolderDto.toDomain(): RemoteFolder = RemoteFolder(
        id = id,
        name = name.orEmpty(),
        sortOrder = sortOrder,
    )

    private companion object {
        const val TAG = "FoldersRepo"
    }
}
