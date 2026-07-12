package com.novamind.app.common.net

import com.novamind.app.common.net.response.ApiResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 笔记域接口（对齐后端 `doc/api-reference.md` §3 Notes）。baseUrl 取 [ApiConfig.apiBaseUrl]（"/" 结尾），
 * 相对路径不带前导斜杠。Authorization 由 [AuthInterceptor] 自动附加，均走统一响应信封。
 */
interface NotesApi {

    /**
     * 笔记列表（keyset 游标分页）。同一接口经参数切换「活跃列表 / 回收站 / 某文件夹内」三视图。
     *
     * - [trashed] `false`=活跃笔记（默认）；`true`=回收站；不传走后端默认（false）。
     * - [folderId] 仅活跃视图有效：`<uuid>`=该文件夹内；`none`=未归档；不传=不按文件夹过滤。
     *   与 `trashed=true` 同用 → 40001。
     * - [limit] 每页条数，缺省 50，后端硬上限 100。
     * - [cursor] 上一页返回的 `nextCursor`；不传=第一页。
     *
     * 返回轻量 [NoteListItemDto]（不含 `content`/`rev`/`schemaVersion`），要正文用 [get]。
     */
    @GET("api/v1.0/notes")
    suspend fun list(
        @Query("trashed") trashed: Boolean? = null,
        @Query("folderId") folderId: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null,
    ): Response<ApiResponse<NotePageViewDto>>

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

    /**
     * 移入/移出回收站（多路 PATCH，body 恰好含一个识别字段；此处用 `trashed`）。
     * `true`=移入回收站，`false`=恢复。返回变更后的 NoteView。
     * 恢复时若笔记已进入永久删除不可逆阶段 → 409/40908。
     */
    @PATCH("api/v1.0/notes/{id}")
    suspend fun setTrashed(
        @Path("id") id: String,
        @Body body: TrashNoteRequestDto,
    ): Response<ApiResponse<NoteDto>>

    /**
     * 永久删除（两步制：笔记须**已在回收站**）。成功 HTTP 204（无响应体，绕过信封）。
     * 仍是活跃笔记 → 409/40906（需先移入回收站）；转写进行中 → 409/40907（可重试）。
     */
    @DELETE("api/v1.0/notes/{id}")
    suspend fun delete(@Path("id") id: String): Response<ApiResponse<Unit>>
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

/** 笔记列表分页视图（NotePageView）：轻量条目 + 下一页游标。 */
@Serializable
data class NotePageViewDto(
    val items: List<NoteListItemDto> = emptyList(),
    /** 下一页游标：null=已到底；否则作为下一页 `cursor` 传回。 */
    val nextCursor: String? = null,
)

/** 笔记列表轻量项（NoteListItem）：字段同 NoteView 但不含 `content`/`rev`/`schemaVersion`。 */
@Serializable
data class NoteListItemDto(
    val id: String,
    val title: String? = null,
    val borderColorHex: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /** 所属文件夹 id；null=未归档。 */
    val folderId: String? = null,
    /** 是否在回收站（deletedAt != null 时为 true）。 */
    val trashed: Boolean = false,
    /** 移入回收站时间；null=活跃。 */
    val deletedAt: String? = null,
)

/** 创建笔记请求体：`title` 可选(≤255)，`content` 必填。 */
@Serializable
data class CreateNoteRequestDto(
    val title: String? = null,
    val content: JsonElement,
)

/** 回收站开关请求体（PATCH 多路之一）：`true`=移入回收站，`false`=恢复。 */
@Serializable
data class TrashNoteRequestDto(
    val trashed: Boolean,
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
