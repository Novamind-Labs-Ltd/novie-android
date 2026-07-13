package com.novamind.app.data

import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.AttachRequestDto
import com.novamind.app.common.net.AttachmentDto
import com.novamind.app.common.net.NetworkModule
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.apiCall
import javax.inject.Inject

/** 笔记附件领域模型（对应后端 AttachmentView）。[downloadUrl] 有时效。 */
data class RemoteAttachment(
    val fileId: String,
    val downloadUrl: String?,
    val expiresAt: String?,
    val originalFilename: String?,
    val sizeBytes: Long?,
)

/**
 * 笔记附件仓库：把已上传（READY）的文件挂到笔记、列出、解除挂载。走统一响应 [apiCall]，
 * DTO 不外泄。上传文件本身由 [FilesRepository] 负责（presign→直传→confirm）。
 */
class AttachmentsRepository @Inject constructor() {

    /** 挂载附件（幂等）。成功返回带签名 downloadUrl 的 [RemoteAttachment]。 */
    suspend fun attach(noteId: String, fileId: String): ApiResult<RemoteAttachment> {
        AppLog.i(TAG) { "attach 开始 noteId=$noteId fileId=$fileId" }
        return when (val r = apiCall {
            NetworkModule.attachmentsApi.attach(noteId, AttachRequestDto(fileId = fileId))
        }) {
            is ApiResult.Success -> {
                AppLog.i(TAG) { "attach 成功 noteId=$noteId fileId=$fileId" }
                ApiResult.Success(r.data?.toDomain())
            }
            is ApiResult.BizError -> {
                AppLog.w(TAG) { "attach 业务错误 noteId=$noteId fileId=$fileId code=${r.code} traceId=${r.traceId}" }
                r
            }
            is ApiResult.NetworkError -> {
                AppLog.w(TAG) { "attach 网络错误 noteId=$noteId fileId=$fileId: ${r.message}" }
                r
            }
        }
    }

    /** 列附件（每项带新鲜签名 URL）。 */
    suspend fun list(noteId: String): ApiResult<List<RemoteAttachment>> {
        AppLog.i(TAG) { "list 开始 noteId=$noteId" }
        return when (val r = apiCall { NetworkModule.attachmentsApi.list(noteId) }) {
            is ApiResult.Success -> {
                val items = r.data?.map { it.toDomain() }.orEmpty()
                AppLog.i(TAG) { "list 成功 noteId=$noteId count=${items.size}" }
                ApiResult.Success(items)
            }
            is ApiResult.BizError -> {
                AppLog.w(TAG) { "list 业务错误 noteId=$noteId code=${r.code} traceId=${r.traceId}" }
                r
            }
            is ApiResult.NetworkError -> {
                AppLog.w(TAG) { "list 网络错误 noteId=$noteId: ${r.message}" }
                r
            }
        }
    }

    /** 解除挂载（204）。 */
    suspend fun detach(noteId: String, fileId: String): ApiResult<Unit> {
        AppLog.i(TAG) { "detach 开始 noteId=$noteId fileId=$fileId" }
        return when (val r = apiCall { NetworkModule.attachmentsApi.detach(noteId, fileId) }) {
            is ApiResult.Success -> {
                AppLog.i(TAG) { "detach 成功 noteId=$noteId fileId=$fileId" }
                ApiResult.Success(Unit)
            }
            is ApiResult.BizError -> {
                AppLog.w(TAG) { "detach 业务错误 noteId=$noteId fileId=$fileId code=${r.code} traceId=${r.traceId}" }
                r
            }
            is ApiResult.NetworkError -> {
                AppLog.w(TAG) { "detach 网络错误 noteId=$noteId fileId=$fileId: ${r.message}" }
                r
            }
        }
    }

    private fun AttachmentDto.toDomain(): RemoteAttachment = RemoteAttachment(
        fileId = fileId,
        downloadUrl = downloadUrl,
        expiresAt = expiresAt,
        originalFilename = originalFilename,
        sizeBytes = sizeBytes,
    )

    private companion object {
        const val TAG = "AttachRepo"
    }
}
