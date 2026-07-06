package com.novamind.app.common.net

import com.novamind.app.common.net.response.ApiResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * 笔记域接口（对齐后端 `doc/api-reference.md` §3 Notes）。baseUrl 取 [ApiConfig.apiBaseUrl]（"/" 结尾），
 * 相对路径不带前导斜杠。Authorization 由 [AuthInterceptor] 自动附加，均走统一响应信封。
 */
interface NotesApi {

    /** 创建笔记（HTTP 201）。`content` 必填。 */
    @POST("api/v1.0/notes")
    suspend fun create(@Body body: CreateNoteRequestDto): Response<ApiResponse<NoteDto>>

    /** 获取单条笔记。不存在/非本人 → 404/40401。 */
    @GET("api/v1.0/notes/{id}")
    suspend fun get(@Path("id") id: String): Response<ApiResponse<NoteDto>>

    /** 全量更新（latest-wins 乐观锁）：成败以返回体的 `applied` 判断，HTTP 恒 200。 */
    @PUT("api/v1.0/notes/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: UpdateNoteRequestDto,
    ): Response<ApiResponse<UpdateNoteResultDto>>
}

/** 笔记视图（NoteView）。`content` 为任意 JSON（jsonb）。 */
@Serializable
data class NoteDto(
    val id: String,
    val rev: Long = 0,
    val schemaVersion: Int? = null,
    val title: String? = null,
    val content: JsonElement? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val borderColorHex: String? = null,
)

/** 创建笔记请求体：`title` 可选(≤255)，`content` 必填。 */
@Serializable
data class CreateNoteRequestDto(
    val title: String? = null,
    val content: JsonElement,
)

/** 全量更新请求体：`rev` 必填(客户端持有的基准版本)，`content` 必填，`title` null 表示清空。 */
@Serializable
data class UpdateNoteRequestDto(
    val rev: Long,
    val title: String? = null,
    val content: JsonElement,
    val schemaVersion: Int? = null,
)

/** 更新结果：`applied=true` 本次写入生效；`false` 表示被更新/相等版本抢先，`note` 为当前胜出版本。 */
@Serializable
data class UpdateNoteResultDto(
    val applied: Boolean = false,
    val note: NoteDto? = null,
)
