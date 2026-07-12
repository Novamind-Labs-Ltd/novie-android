package com.novamind.app.common.net

import com.novamind.app.common.net.response.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 文件夹域接口（对齐后端 `doc/api-reference.md` §4 Folders）。baseUrl 取 [ApiConfig.apiBaseUrl]（"/" 结尾），
 * 相对路径不带前导斜杠。Authorization 由 [AuthInterceptor] 自动附加，均走统一响应信封。
 */
interface FoldersApi {

    /** 新建文件夹（HTTP 201）。`name` 可选（≤255）。 */
    @POST("api/v1.0/folders")
    suspend fun create(@Body body: CreateFolderRequestDto): Response<ApiResponse<FolderDto>>

    /**
     * 文件夹列表（keyset 游标分页），仅活跃文件夹，按 `sortOrder ASC, id ASC`。
     * [limit] 缺省 50、硬上限 100；[cursor] 上一页 nextCursor，不传=第一页。
     */
    @GET("api/v1.0/folders")
    suspend fun list(
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null,
    ): Response<ApiResponse<FolderPageViewDto>>

    /**
     * 多路 PATCH：body 恰好含一个识别字段——`name`（重命名）或 `trashed`（移入/移出回收站）。
     * 返回变更后的 FolderView。文件夹不存在/非本人 → 404/40407；在回收站中 → 409/40909。
     */
    @PATCH("api/v1.0/folders/{id}")
    suspend fun patch(
        @Path("id") id: String,
        @Body body: PatchFolderRequestDto,
    ): Response<ApiResponse<FolderDto>>

    /**
     * 拖拽重排序：在单事务内按 `orderedIds` 重写调用者所有活跃文件夹的 `sortOrder`。
     * 成功 HTTP 204（无响应体，绕过信封）；客户端随后重拉 [list] 取新顺序。
     * `orderedIds` 缺失/超 1000 → 400/40001；含重复 id → 400；含未知/非本人/已回收 id → 404/40407。
     */
    @PUT("api/v1.0/folders/order")
    suspend fun reorder(@Body body: ReorderFoldersRequestDto): Response<ApiResponse<Unit>>
}

/** 文件夹视图（FolderView）。 */
@Serializable
data class FolderDto(
    val id: String,
    val name: String? = null,
    val sortOrder: Int = 0,
)

/** 文件夹列表分页视图（FolderPageView）：条目 + 下一页游标。 */
@Serializable
data class FolderPageViewDto(
    val items: List<FolderDto> = emptyList(),
    val nextCursor: String? = null,
)

/** 新建文件夹请求体：`name` 可选（≤255）。 */
@Serializable
data class CreateFolderRequestDto(
    val name: String? = null,
)

/**
 * 文件夹多路 PATCH 请求体：`name`（重命名）与 `trashed`（回收站开关）**二选一**。
 * `NetworkModule.json` 的 `explicitNulls=false` 会省略未设的字段，故仅需设置其一。
 */
@Serializable
data class PatchFolderRequestDto(
    val name: String? = null,
    val trashed: Boolean? = null,
)

/** 拖拽重排序请求体：`orderedIds` 为按目标顺序排列的文件夹 id（≤1000）。 */
@Serializable
data class ReorderFoldersRequestDto(
    val orderedIds: List<String>,
)
