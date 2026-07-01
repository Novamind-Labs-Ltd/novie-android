package com.novamind.app.data.tasks

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Google Tasks REST API v1。Authorization 头由 [GoogleTasksNetwork] 的拦截器注入。
 * baseUrl 为 https://developers.google.com/workspace/tasks/reference/rest/v1/tasks/list?hl=zh-cn
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
        // 已完成的任务会被 Google 自动置为 hidden，需 showHidden=true 才会返回，否则日历里看不到已完成项。
        @Query("showHidden") showHidden: Boolean = true,
        @Query("maxResults") maxResults: Int = 100,
    ): TasksResponse

    /** 局部更新任务（如置 status=completed）。需读写 scope `tasks`。 */
    @PATCH("lists/{tasklist}/tasks/{task}")
    suspend fun patchTask(
        @Path("tasklist") taskListId: String,
        @Path("task") taskId: String,
        @Body body: TaskPatchDto,
    ): TaskDto
}
