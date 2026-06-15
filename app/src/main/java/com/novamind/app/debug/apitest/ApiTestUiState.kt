package com.novamind.app.debug.apitest

/**
 * 接口测试页 UI 状态（不可变，单一数据源）。
 */
data class ApiTestUiState(
    val isLoading: Boolean = false,
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
