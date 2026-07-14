package com.novamind.app.data

import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.NetworkModule
import com.novamind.app.common.net.TranscriptionTaskDto
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.apiCall
import javax.inject.Inject

/**
 * 转写结果仓库（对接后端 §9）：拉取某笔记的转写任务、标记已消费。
 * [list] 走统一信封；[consume] 成功为 HTTP 200 无信封，best-effort 返回是否成功。
 */
class TranscriptionRepository @Inject constructor() {

    /** 列出某笔记名下所有转写任务（含结果）。 */
    suspend fun list(noteId: String): ApiResult<List<TranscriptionTaskDto>> =
        apiCall { NetworkModule.transcriptionApi.list(noteId) }

    /** 标记转写任务已消费（幂等）；网络/服务异常时吞掉，返回 false（不影响主流程）。 */
    suspend fun consume(noteId: String, jobId: String): Boolean =
        runCatching { NetworkModule.transcriptionApi.consume(noteId, jobId).isSuccessful }
            .onFailure { AppLog.w(TAG) { "consume 失败 noteId=$noteId jobId=$jobId: ${it.message}" } }
            .getOrDefault(false)

    private companion object {
        const val TAG = "TranscriptionRepo"
    }
}
