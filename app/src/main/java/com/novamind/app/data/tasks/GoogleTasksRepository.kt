package com.novamind.app.data.tasks

import com.novamind.app.common.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.time.LocalDate

/**
 * Google Tasks 数据仓库。授权 token 由 [com.novamind.app.common.google.GoogleTokenProvider] 提供，
 * scope 需含 `tasks`（读写，完成任务需要）。
 */
interface GoogleTasksRepository {
    /** 拉取截止日期为 [date] 的任务（跨所有任务列表）。 */
    suspend fun tasksOn(date: LocalDate): List<CalendarTask>

    /** 设置任务完成状态（true=已完成，false=未完成）。失败抛异常，由调用方回滚乐观更新。 */
    suspend fun setCompleted(listId: String, taskId: String, completed: Boolean)

    /** 在默认任务列表创建任务。失败抛异常，由调用方提示。 */
    suspend fun createTask(title: String, notes: String?, due: LocalDate?)

    /** 更新任务的标题/描述/截止日期。notes 传 null 表示清空描述。失败抛异常。 */
    suspend fun updateTask(listId: String, taskId: String, title: String, notes: String?, due: LocalDate?)

    /** 删除任务。失败抛异常，由调用方回滚乐观更新。 */
    suspend fun deleteTask(listId: String, taskId: String)
}

class GoogleTasksRepositoryImpl(
    private val api: GoogleTasksApi = GoogleTasksNetwork.api,
) : GoogleTasksRepository {

    override suspend fun tasksOn(date: LocalDate): List<CalendarTask> = withContext(Dispatchers.IO) {
        try {
            val taskLists = api.taskLists().items
            AppLog.d(TAG) { "taskLists count=${taskLists.size} items=" +
                    taskLists.joinToString { "[id=${it.id}, title=${it.title}]" } }
            taskLists
                .mapNotNull { it.id }
                .flatMap { listId -> api.tasks(listId).items.mapNotNull { it.toDomain(listId) } }
                .filter { it.due == date }
                // 未完成在前、已完成在后，同组按标题排序。
                .sortedWith(compareBy({ it.isCompleted }, { it.title }))
        } catch (e: HttpException) {
            // 打印 Google 返回的真实原因，便于区分「Tasks API 未启用」vs「scope 不足」等。
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            AppLog.w(TAG, e) { "tasks fetch failed http=${e.code()} body=$body" }
            throw e
        }
    }

    override suspend fun updateTask(
        listId: String,
        taskId: String,
        title: String,
        notes: String?,
        due: LocalDate?,
    ): Unit = withContext(Dispatchers.IO) {
        try {
            api.patchTask(
                taskListId = listId,
                taskId = taskId,
                body = TaskPatchDto(
                    title = title,
                    // 清空描述需发空串（null 会被序列化省略、字段不更新）。
                    notes = notes ?: "",
                    due = due?.let { "${it}T00:00:00.000Z" },
                ),
            )
            AppLog.d(TAG) { "updateTask ok: listId=$listId taskId=$taskId due=$due" }
        } catch (e: HttpException) {
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            AppLog.w(TAG, e) { "updateTask failed http=${e.code()} body=$body" }
            throw e
        }
    }

    override suspend fun deleteTask(listId: String, taskId: String): Unit = withContext(Dispatchers.IO) {
        try {
            api.deleteTask(listId, taskId)
            AppLog.d(TAG) { "deleteTask ok: listId=$listId taskId=$taskId" }
        } catch (e: HttpException) {
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            AppLog.w(TAG, e) { "deleteTask failed http=${e.code()} body=$body" }
            throw e
        }
    }

    override suspend fun setCompleted(listId: String, taskId: String, completed: Boolean): Unit =
        withContext(Dispatchers.IO) {
            try {
                val status = if (completed) "completed" else "needsAction"
                api.patchTask(listId, taskId, TaskPatchDto(status = status))
                AppLog.d(TAG) { "setCompleted ok: listId=$listId taskId=$taskId completed=$completed" }
            } catch (e: HttpException) {
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                AppLog.w(TAG, e) { "setCompleted failed http=${e.code()} body=$body" }
                throw e
            }
        }

    override suspend fun createTask(title: String, notes: String?, due: LocalDate?): Unit =
        withContext(Dispatchers.IO) {
            try {
                val created = api.insertTask(
                    taskListId = DEFAULT_LIST,
                    body = TaskInsertDto(
                        title = title,
                        notes = notes?.takeIf { it.isNotBlank() },
                        // Google 只识别日期部分，时间固定 00:00:00Z。
                        due = due?.let { "${it}T00:00:00.000Z" },
                    ),
                )
                AppLog.d(TAG) { "createTask ok: id=${created.id} title=$title due=$due" }
            } catch (e: HttpException) {
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                AppLog.w(TAG, e) { "createTask failed http=${e.code()} body=$body" }
                throw e
            }
        }

    private fun TaskDto.toDomain(listId: String): CalendarTask? {
        val id = id ?: return null
        return CalendarTask(
            id = id,
            listId = listId,
            title = title?.takeIf { it.isNotBlank() } ?: "(No title)",
            // due 为 RFC3339，但仅日期有意义：取日期部分。
            due = due?.substringBefore("T")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            isCompleted = status == "completed",
            notes = notes?.takeIf { it.isNotBlank() },
        )
    }

    private companion object {
        const val TAG = "CalendarTasks"

        /** Google Tasks 的默认列表别名。 */
        const val DEFAULT_LIST = "@default"
    }
}
