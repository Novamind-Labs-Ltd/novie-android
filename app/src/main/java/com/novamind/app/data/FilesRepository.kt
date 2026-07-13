package com.novamind.app.data

import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.FileView
import com.novamind.app.common.net.NetworkModule
import com.novamind.app.common.net.PresignReq
import com.novamind.app.common.net.PresignResp
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.apiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * 文件上传仓库：封装后端 files 三段式（presign → 直传对象存储 → confirm），
 * 对上只暴露「给个本地文件，返回后端 fileId」。走统一响应 [apiCall]；直传步骤直连对象存储不走信封。
 *
 * 鉴权由 [com.novamind.app.common.net.AuthInterceptor] 自动附加；游客（无 token）会在 presign
 * 阶段得到 401，调用方（上传器）应在此之前短路。
 */
class FilesRepository @Inject constructor() {

    /**
     * 上传单个文件，成功返回后端 `fileId`（可用于挂载到笔记的 attachments）。
     *
     * @param file 本地文件（须存在）
     * @param contentType 须在后端允许类型内（如录音 [com.novamind.app.common.config.AppConfig.Media.AUDIO_MIME]）
     */
    suspend fun uploadFile(file: File, contentType: String): Result<String> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() <= 0L) {
            return@withContext Result.failure(IOException("File does not exist or is empty: ${file.name}"))
        }
        // 1) 申请预签名
        val presign = when (val r = apiCall {
            NetworkModule.filesApi.presign(
                PresignReq(
                    contentType = contentType,
                    declaredSizeBytes = file.length(),
                    originalFilename = file.name,
                ),
            )
        }) {
            is ApiResult.Success -> r.data ?: return@withContext fail("presign response is empty")
            is ApiResult.BizError -> return@withContext fail("presign failed code=${r.code} ${r.message}")
            is ApiResult.NetworkError -> return@withContext Result.failure(r.cause ?: IOException("presign network error"))
        }

        // 2) 直传对象存储（表单字段在前、文件名为 "file"；不走信封）
        val uploaded = runCatching { putToStorage(presign, file, contentType) }
            .getOrElse { return@withContext Result.failure(it) }
        if (!uploaded) return@withContext fail("Direct upload to object storage failed")

        // 3) 确认落库，校验 READY
        val view: FileView = when (val r = apiCall { NetworkModule.filesApi.confirm(presign.fileId) }) {
            is ApiResult.Success -> r.data ?: return@withContext fail("confirm response is empty")
            is ApiResult.BizError -> return@withContext fail("confirm failed code=${r.code} ${r.message}")
            is ApiResult.NetworkError -> return@withContext Result.failure(r.cause ?: IOException("confirm network error"))
        }
        if (view.status != STATUS_READY) return@withContext fail("confirm status abnormal: ${view.status}")

        AppLog.i(TAG) { "upload ok fileId=${presign.fileId} size=${file.length()}" }
        Result.success(presign.fileId)
    }

    /**
     * 直传对象存储。默认走 OSS **预签名 PUT**（当前后端契约：`upload.method=PUT` + `headers`）：
     * 文件原始字节作请求体，headers 原样带上（Content-Type 由 body 的 media type 提供，避免重复头）。
     * 仅当后端返回 S3 预签名 POST（`fields` 非空且非 PUT）时才回退到 multipart POST。成功返回 true。
     */
    private suspend fun putToStorage(presign: PresignResp, file: File, contentType: String): Boolean {
        val upload = presign.upload
        val isPost = upload.method?.equals("POST", ignoreCase = true) == true ||
            (upload.method == null && upload.fields.isNotEmpty())

        val resp = if (isPost) {
            // 兼容：S3 预签名 POST（表单字段在前、文件 part 名 "file"）
            val media = contentType.toMediaTypeOrNull()
            val fields = LinkedHashMap<String, okhttp3.RequestBody>().apply {
                upload.fields.forEach { (k, v) -> put(k, v.toRequestBody(null)) }
            }
            val filePart = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody(media))
            NetworkModule.filesApi.uploadToStorage(upload.url, fields, filePart)
        } else {
            // OSS 预签名 PUT：Content-Type 取 headers 指定值（否则回退调用方 contentType），
            // 其余 header 原样带上；Content-Type 走 body media type，从 headerMap 剔除避免重复。
            val ct = upload.headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                ?.value ?: contentType
            val headerMap = upload.headers.filterKeys { !it.equals("Content-Type", ignoreCase = true) }
            val body = file.asRequestBody(ct.toMediaTypeOrNull())
            NetworkModule.filesApi.uploadPut(upload.url, headerMap, body)
        }

        if (!resp.isSuccessful) {
            AppLog.w(TAG) { "storage upload http=${resp.code()} method=${if (isPost) "POST" else "PUT"}" }
        }
        resp.body()?.close()
        resp.errorBody()?.close()
        return resp.isSuccessful
    }

    private fun fail(msg: String): Result<String> {
        AppLog.w(TAG) { msg }
        return Result.failure(IOException(msg))
    }

    private companion object {
        const val TAG = "FilesRepo"
        const val STATUS_READY = "READY"
    }
}
