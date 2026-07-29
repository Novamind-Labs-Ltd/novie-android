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
import com.novamind.app.common.net.response.mapLogged
import com.novamind.app.feature.create.folder.RemoteFolder
import com.novamind.app.feature.create.folder.RemoteFolderPage

/**
 * [FoldersRepository] 的网络实现：走统一响应 [apiCall]，把 DTO 映射为领域模型，DTO 不外泄。
 * 每个能力都打日志（开始/成功/业务错误/网络错误），便于联调与线上排障。
 */
class RemoteFoldersRepository : FoldersRepository {

    override suspend fun listFolders(limit: Int?, cursor: String?): ApiResult<RemoteFolderPage> {
        AppLog.i(TAG) { "listFolders 开始 limit=$limit hasCursor=${cursor != null}" }
        return apiCall { NetworkModule.foldersApi.list(limit = limit, cursor = cursor) }.mapLogged(
            tag = TAG,
            op = "listFolders",
            transform = { it?.toDomain() ?: RemoteFolderPage(emptyList(), null) },
            successLog = { "listFolders 成功 count=${it?.items?.size} hasNext=${it?.nextCursor != null}" },
        )
    }

    override suspend fun createFolder(name: String?): ApiResult<RemoteFolder> {
        AppLog.i(TAG) { "createFolder 开始 nameLen=${name?.length ?: 0}" }
        return apiCall {
            NetworkModule.foldersApi.create(CreateFolderRequestDto(name = name))
        }.mapLogged(
            tag = TAG,
            op = "createFolder",
            transform = { it?.toDomain() },
            successLog = { "createFolder 成功 id=${it?.id}" },
        )
    }

    override suspend fun renameFolder(id: String, name: String): ApiResult<RemoteFolder> =
        patch(id, PatchFolderRequestDto(name = name), op = "renameFolder")

    override suspend fun setTrashed(id: String, trashed: Boolean): ApiResult<RemoteFolder> =
        patch(id, PatchFolderRequestDto(trashed = trashed), op = "setTrashed")

    override suspend fun reorderFolders(orderedIds: List<String>): ApiResult<Unit> {
        AppLog.i(TAG) { "reorderFolders 开始 count=${orderedIds.size}" }
        return apiCall {
            NetworkModule.foldersApi.reorder(ReorderFoldersRequestDto(orderedIds = orderedIds))
        }.mapLogged(
            tag = TAG,
            op = "reorderFolders",
            transform = { Unit },
            successLog = { "reorderFolders 成功 count=${orderedIds.size}" },
        )
    }

    private suspend fun patch(id: String, body: PatchFolderRequestDto, op: String): ApiResult<RemoteFolder> {
        AppLog.i(TAG) { "$op 开始 id=$id" }
        return apiCall { NetworkModule.foldersApi.patch(id, body) }.mapLogged(
            tag = TAG,
            op = "$op id=$id",
            transform = { it?.toDomain() },
            successLog = { "$op 成功 id=$id sortOrder=${it?.sortOrder}" },
        )
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
        noteCount = noteCount,
    )

    private companion object {
        const val TAG = "FoldersRepo"
    }
}
