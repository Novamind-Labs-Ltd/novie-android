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
