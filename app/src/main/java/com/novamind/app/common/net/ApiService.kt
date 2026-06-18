package com.novamind.app.common.net

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url

/**
 * Retrofit 接口定义。
 *
 * 当前两个接口（GET 列表 / multipart 上传）来自调试工具箱，目标地址在运行时动态决定，
 * 故统一用 [Url] 传入绝对地址；响应保持 [ResponseBody] 原文，交由各 ViewModel 做容错解析，
 * 不绑定具体响应模型（接口返回结构尚不固定）。返回 [Response] 以便读取状态码。
 */
interface ApiService {

    /** 通用 GET：传入完整 URL（含 query），返回原始响应体。 */
    @GET
    suspend fun get(@Url url: String): Response<ResponseBody>

    /** 通用 multipart 上传：传入完整 URL 与一个文件 part，返回原始响应体。 */
    @Multipart
    @POST
    suspend fun upload(
        @Url url: String,
        @Part part: MultipartBody.Part,
    ): Response<ResponseBody>
}
