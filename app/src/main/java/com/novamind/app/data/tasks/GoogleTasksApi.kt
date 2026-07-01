package com.novamind.app.data.tasks

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Google Tasks REST API v1（只读）。Authorization 头由 [GoogleTasksNetwork] 的拦截器注入。
 * baseUrl 为 https://tasks.googleapis.com/tasks/v1/。
 */
interface GoogleTasksApi {

    /** 当前用户的任务列表。 */
    @GET("users/@me/lists")
    suspend fun taskLists(): TaskListsResponse

    /** 某任务列表下的任务。含已完成、按需过滤截止区间。 */
    @GET("lists/{tasklist}/tasks")
    suspend fun tasks(
        @Path("tasklist") taskListId: String,
        @Query("showCompleted") showCompleted: Boolean = true,
        @Query("showHidden") showHidden: Boolean = false,
        @Query("maxResults") maxResults: Int = 100,
    ): TasksResponse
}
