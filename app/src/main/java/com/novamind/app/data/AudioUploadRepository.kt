package com.novamind.app.data

import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.AudioChunkAck
import com.novamind.app.common.net.ChunkTargetReq
import com.novamind.app.common.net.CompleteAudioReq
import com.novamind.app.common.net.InitiateAudioReq
import com.novamind.app.common.net.NetworkModule
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.apiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import javax.inject.Inject

/**
 * 源录音上传仓库（对接后端 §7，X lane，断点续传）：initiate → 循环(chunk-target + 直传该片) → complete。
 * 直传该片复用 [NetworkModule.filesApi] 的 `uploadPut`（原样带 headers，读回 ETag）；元数据端点走统一 [apiCall]。
 *
 * @return [ApiResult.Success] 携带转写 jobId（幂等重复完成时可能为 null）；失败为 BizError / NetworkError。
 */
class AudioUploadRepository @Inject constructor() {

    /**
     * @param onProgress 上传进度回调（已直传字节 / 总字节）；在 IO 线程被高频调用，调用方需自行节流与切主线程。
     */
    suspend fun uploadNoteAudio(
        noteId: String,
        file: File,
        contentType: String,
        durationMs: Long,
        onProgress: (uploadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): ApiResult<String?> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() <= 0L) {
            return@withContext ApiResult.NetworkError(cause = IOException("Audio file missing or empty: ${file.name}"))
        }
        val total = file.length()

        // 1) initiate
        val init = when (val r = apiCall {
            NetworkModule.noteAudioApi.initiate(
                noteId,
                InitiateAudioReq(contentType = contentType, totalSizeBytes = total, declaredDurationMs = durationMs),
            )
        }) {
            is ApiResult.Success -> r.data ?: return@withContext fail("initiate response is empty")
            is ApiResult.BizError -> return@withContext r
            is ApiResult.NetworkError -> return@withContext r
        }
        val uploadId = init.uploadId
        val chunkSize = init.chunkSize.coerceAtLeast(1L)
        AppLog.i(TAG) { "initiate ok noteId=$noteId uploadId=$uploadId chunkSize=$chunkSize total=$total" }

        // 进度起点：先回报 0，避免 UI 卡在上一次的值
        onProgress(0L, total)

        // 2) 逐片：chunk-target → 直传 → 收 ETag
        val acks = ArrayList<AudioChunkAck>()
        try {
            RandomAccessFile(file, "r").use { raf ->
                var index = 0
                var offset = 0L
                while (offset < total) {
                    val size = minOf(chunkSize, total - offset).toInt()
                    val target = when (val r = apiCall {
                        NetworkModule.noteAudioApi.chunkTarget(noteId, uploadId, ChunkTargetReq(index, size.toLong()))
                    }) {
                        is ApiResult.Success -> r.data ?: return@withContext fail("chunk-target response is empty")
                        is ApiResult.BizError -> return@withContext r
                        is ApiResult.NetworkError -> return@withContext r
                    }
                    val bytes = ByteArray(size)
                    raf.seek(offset)
                    raf.readFully(bytes)
                    val base = offset  // 本片之前已直传的字节数
                    val etag = putChunk(target.url, target.headers, bytes) { written ->
                        onProgress((base + written).coerceAtMost(total), total)
                    } ?: return@withContext fail("chunk #$index direct upload failed")
                    acks.add(AudioChunkAck(chunkIndex = index, etag = etag))
                    offset += size
                    index++
                    onProgress(offset.coerceAtMost(total), total)  // 该片直传完，进度对齐到片尾
                }
            }
        } catch (e: IOException) {
            AppLog.w(TAG) { "read/upload chunk failed: ${e.message}" }
            return@withContext ApiResult.NetworkError(cause = e)
        }

        // 3) complete → jobId
        return@withContext when (val r = apiCall {
            NetworkModule.noteAudioApi.complete(noteId, uploadId, CompleteAudioReq(acks))
        }) {
            is ApiResult.Success -> {
                AppLog.i(TAG) { "complete ok noteId=$noteId jobId=${r.data?.jobId}" }
                ApiResult.Success<String?>(r.data?.jobId)
            }
            is ApiResult.BizError -> {
                AppLog.w(TAG) { "complete 业务错误 noteId=$noteId code=${r.code} traceId=${r.traceId}" }
                r
            }
            is ApiResult.NetworkError -> {
                AppLog.w(TAG) { "complete 网络错误 noteId=$noteId: ${r.message}" }
                r
            }
        }
    }

    /**
     * 直传一片到对象存储（PUT）。**严格原样透传后端返回的 headers**：Content-Type 只有在后端
     * `headers` 里显式给出时才发——OSS 的 UploadPart（分片）通常不对 Content-Type 签名，
     * 多发一个 Content-Type 会让服务端重算的 StringToSign 多一行，导致 `SignatureDoesNotMatch`。
     * 返回响应 ETag；失败返回 null。
     * @param onWritten 本片已写出的累计字节数（用于细粒度进度）。
     */
    private suspend fun putChunk(
        url: String,
        headers: Map<String, String>,
        bytes: ByteArray,
        onWritten: (Long) -> Unit,
    ): String? {
        // Content-Type 由 RequestBody 承载（避免与 OkHttp 重复设置）；后端没给就传 null → 不发该头
        val ct = headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value
        val headerMap = headers.filterKeys { !it.equals("Content-Type", ignoreCase = true) }
        val body = ProgressRequestBody(bytes.toRequestBody(ct?.toMediaTypeOrNull()), onWritten)
        val resp = NetworkModule.filesApi.uploadPut(url, headerMap, body)
        val etag = resp.headers()["ETag"] ?: resp.headers()["Etag"]
        val ok = resp.isSuccessful
        resp.body()?.close()
        resp.errorBody()?.close()
        if (!ok) {
            AppLog.w(TAG) { "chunk PUT http=${resp.code()}" }
            return null
        }
        // ETag 可能为空（部分存储配置）；complete 允许 null etag，这里回传空串占位以保留分片顺序
        return etag ?: ""
    }

    private fun fail(msg: String): ApiResult<String?> {
        AppLog.w(TAG) { msg }
        return ApiResult.NetworkError(message = msg)
    }

    /** 包一层 okio，在写出字节时回报累计进度（本片内的字节数）。 */
    private class ProgressRequestBody(
        private val delegate: RequestBody,
        private val onWritten: (Long) -> Unit,
    ) : RequestBody() {
        override fun contentType(): MediaType? = delegate.contentType()
        override fun contentLength(): Long = delegate.contentLength()
        override fun writeTo(sink: BufferedSink) {
            val counting = object : ForwardingSink(sink) {
                private var written = 0L
                override fun write(source: Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    written += byteCount
                    onWritten(written)
                }
            }
            val buffered = counting.buffer()
            delegate.writeTo(buffered)
            buffered.flush()
        }
    }

    private companion object {
        const val TAG = "AudioUploadRepo"
    }
}
