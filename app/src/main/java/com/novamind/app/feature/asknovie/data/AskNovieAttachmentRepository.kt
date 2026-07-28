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

    /** 选择附件后立即上传并关联到当前会话，成功返回可直接交给 Agent 的 fileId。 */
    suspend fun uploadAndAttach(
        conversationId: String,
        attachment: Attachment,
    ): Result<String> {
        val contentType = contentTypeOf(attachment)
            ?: return Result.failure(IOException("Unsupported attachment type: ${attachment.name}"))
        val fileId = filesRepository.uploadFile(File(attachment.path), contentType)
            .getOrElse { return Result.failure(it) }
        return when (val attached = apiCall {
            NetworkModule.conversationAttachmentsApi.attach(
                conversationId,
                ConversationAttachRequest(fileId),
            )
        }) {
            is ApiResult.Success -> Result.success(fileId)
            is ApiResult.BizError -> Result.failure(
                IOException(attached.message ?: "Could not attach ${attachment.name}"),
            )
            is ApiResult.NetworkError -> Result.failure(
                attached.cause ?: IOException(attached.message ?: "Attachment network error"),
            )
        }
    }

    suspend fun prepareForTurn(
        conversationId: String,
        newAttachments: List<Attachment>,
    ): Result<List<String>> {
        val attachmentIds = mutableListOf<String>()
        newAttachments
            .filter { it.type == AttachType.Image || it.type == AttachType.File }
            .forEach { attachment ->
                val fileId = attachment.remoteFileId
                    ?: uploadAndAttach(conversationId, attachment).getOrElse {
                        return Result.failure(it)
                    }
                attachmentIds += fileId
            }
        return Result.success(attachmentIds)
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
