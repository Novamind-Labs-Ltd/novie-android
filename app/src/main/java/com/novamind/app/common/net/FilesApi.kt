package com.novamind.app.common.net

import com.novamind.app.common.net.response.ApiResponse
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path
import retrofit2.http.Url

/**
 * 文件域接口（对齐后端 `doc/api-reference.md` §5 Files）。baseUrl 取 [ApiConfig.apiBaseUrl]（"/" 结尾），
 * 故相对路径不带前导斜杠。Authorization 由 [AuthInterceptor] 统一附加。
 *
 * 三段式：presign（申请预签名）→ 直传对象存储（[uploadToStorage]）→ confirm（确认落库）。
 */
interface FilesApi {

    /** 申请上传预签名。 */
    @POST("api/v1.0/files/presign")
    suspend fun presign(@Body body: PresignReq): Response<ApiResponse<PresignResp>>

    /**
     * 直传对象存储：S3 presigned POST。**表单字段（policy 等）必须在文件之前**，
     * 文件 part 名固定为 `file`。此端点直连对象存储，**不走后端信封**，返回原始响应。
     *
     * @param url    presign 返回的 [UploadTarget.url]
     * @param fields presign 返回的 [UploadTarget.fields]（用 LinkedHashMap 保序）
     * @param file   文件 part（`MultipartBody.Part.createFormData("file", filename, body)`）
     */
    @Multipart
    @POST
    suspend fun uploadToStorage(
        @Url url: String,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part file: MultipartBody.Part,
    ): Response<ResponseBody>

    /** 确认上传完成，后端核对大小后置为 READY。 */
    @POST("api/v1.0/files/{id}/confirm")
    suspend fun confirm(@Path("id") id: String): Response<ApiResponse<FileView>>
}

/** presign 请求体。 */
@Serializable
data class PresignReq(
    val contentType: String,
    val declaredSizeBytes: Long,
    val originalFilename: String,
)

/** presign 响应：客户端用 [upload] 直传，成功后调 confirm(fileId)。 */
@Serializable
data class PresignResp(
    val fileId: String,
    val upload: UploadTarget,
    val expiresAt: String? = null,
)

/** 直传目标：POST 到 [url]，[fields] 为必须随表单一起提交的签名字段。 */
@Serializable
data class UploadTarget(
    val url: String,
    val fields: Map<String, String> = emptyMap(),
)

/** 文件视图（confirm 返回）。`status`：PENDING | READY | DELETED。 */
@Serializable
data class FileView(
    val id: String,
    val status: String,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val originalFilename: String? = null,
    val createdAt: String? = null,
    val confirmedAt: String? = null,
)
