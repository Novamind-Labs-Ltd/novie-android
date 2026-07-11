package com.novamind.app.debug.apitest

/**
 * 接口测试目标：不同接口的基础地址、分页参数名与默认值。
 *
 * 新增接口时在此追加一项即可，UI / ViewModel 自动适配。
 */
enum class ApiTarget(
    val title: String,        // 页面标题
    val baseUrl: String,      // 基础地址（不含 query）
    val pageParam: String,    // 页码参数名（拼到 URL 上）
    val sizeParam: String,    // 每页条数参数名（拼到 URL 上）
    val defaultPage: String,  // 页码默认值
    val defaultSize: String,  // 每页条数默认值
) {
    /** 原有接口：page / size。 */
    ITEMS(
        title = "API Test",
        baseUrl = "https://121.41.207.114/items",
        pageParam = "page",
        sizeParam = "size",
        defaultPage = "1",
        defaultSize = "10",
    ),

    /** 用户接口：pageNum / pageSize。 */
    USERS(
        title = "User API Test",
        baseUrl = "http://121.41.207.114:8080/api/users",
        pageParam = "pageNum",
        sizeParam = "pageSize",
        defaultPage = "1",
        defaultSize = "10",
    ),
}

/**
 * 接口测试页 UI 状态（不可变，单一数据源）。
 */
data class ApiTestUiState(
    val target: ApiTarget = ApiTarget.ITEMS,  // 当前测试的接口目标
    val isLoading: Boolean = false,
    val page: String = ApiTarget.ITEMS.defaultPage,   // 可配置的分页页码
    val size: String = ApiTarget.ITEMS.defaultSize,   // 可配置的每页条数
    val items: List<ApiItem> = emptyList(),
    val rawPreview: String = "",          // 原始响应预览（便于调试）
    val errorMessage: String? = null,
    val hasLoaded: Boolean = false,       // 是否已发起过至少一次请求
)

/**
 * 列表项。接口字段不确定，故除常见的 id/title/subtitle 外，
 * 保留 [extra] 兜底展示其余键值对。
 */
data class ApiItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val extra: String,
)
