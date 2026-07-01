package com.novamind.app.data.tasks

import kotlinx.serialization.Serializable

/**
 * Google Tasks API v1 精简 DTO。JSON 已开启 ignoreUnknownKeys，其余字段忽略。
 * 文档：https://developers.google.com/tasks/reference/rest
 */
@Serializable
data class TaskListsResponse(
    val items: List<TaskListDto> = emptyList(),
)

@Serializable
data class TaskListDto(
    val id: String? = null,
    val title: String? = null,
)

@Serializable
data class TasksResponse(
    val items: List<TaskDto> = emptyList(),
)

@Serializable
data class TaskDto(
    val id: String? = null,
    val title: String? = null,
    val notes: String? = null,
    /** needsAction | completed */
    val status: String? = null,
    /** RFC3339，仅日期有意义（如 2026-07-01T00:00:00.000Z）。 */
    val due: String? = null,
    val completed: String? = null,
)

/** PATCH 局部更新请求体：仅携带需变更的字段。 */
@Serializable
data class TaskPatchDto(
    /** needsAction | completed。置 completed 时 Google 自动写入完成时间。 */
    val status: String,
)
