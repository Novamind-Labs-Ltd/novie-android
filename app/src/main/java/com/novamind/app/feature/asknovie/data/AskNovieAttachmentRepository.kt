package com.novamind.app.feature.asknovie.data

import com.novamind.app.common.net.ConversationAttachRequest
import com.novamind.app.common.net.NetworkModule
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.apiCall
import com.novamind.app.data.FilesRepository
import com.novamind.app.feature.asknovie.AttachType
import com.novamind.app.feature.asknovie.Attachment
import java.io.File
import java.io.IOException

/** 上传并关联 Ask Novie 图片/PDF，返回本轮必须传给 Agent 的全部 active fileId。 */
object AskNovieAttachmentRepository {
    private val filesRepository = FilesRepository()

    suspend fun prepareForTurn(
        conversationId: String,
        newAttachments: List<Attachment>,
    ): Result<List<String>> {
        newAttachments
            .filter { it.type == AttachType.Image || it.type == AttachType.File }
            .forEach { attachment ->
                val contentType = contentTypeOf(attachment)
                    ?: return Result.failure(IOException("Unsupported attachment type: ${attachment.name}"))
                val fileId = filesRepository.uploadFile(File(attachment.path), contentType)
                    .getOrElse { return Result.failure(it) }
                when (val attached = apiCall {
                    NetworkModule.conversationAttachmentsApi.attach(
                        conversationId,
                        ConversationAttachRequest(fileId),
                    )
                }) {
                    is ApiResult.Success -> Unit
                    is ApiResult.BizError -> return Result.failure(
                        IOException(attached.message ?: "Could not attach ${attachment.name}"),
                    )
                    is ApiResult.NetworkError -> return Result.failure(
                        attached.cause ?: IOException(attached.message ?: "Attachment network error"),
                    )
                }
            }

        return when (val listed = apiCall {
            NetworkModule.conversationAttachmentsApi.list(conversationId)
        }) {
            is ApiResult.Success -> Result.success(
                listed.data.orEmpty()
                    .filter { it.status == "ACTIVE" }
                    .map { it.fileId },
            )
            is ApiResult.BizError -> Result.failure(
                IOException(listed.message ?: "Could not load conversation attachments"),
            )
            is ApiResult.NetworkError -> Result.failure(
                listed.cause ?: IOException(listed.message ?: "Attachment network error"),
            )
        }
    }

    private fun contentTypeOf(attachment: Attachment): String? {
        val extension = attachment.name.substringAfterLast('.', "")
            .ifBlank { File(attachment.path).extension }
            .lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            else -> null
        }
    }
}
