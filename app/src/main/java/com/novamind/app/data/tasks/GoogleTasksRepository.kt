package com.novamind.app.data.tasks

import com.novamind.app.util.LogUtils
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

    /** 将任务标记为已完成。失败抛异常，由调用方回滚乐观更新。 */
    suspend fun completeTask(listId: String, taskId: String)

    /** 在默认任务列表创建任务。失败抛异常，由调用方提示。 */
    suspend fun createTask(title: String, notes: String?, due: LocalDate?)

    /** 更新任务的标题/描述/截止日期。notes 传 null 表示清空描述。失败抛异常。 */
    suspend fun updateTask(listId: String, taskId: String, title: String, notes: String?, due: LocalDate?)
}

class GoogleTasksRepositoryImpl(
    private val api: GoogleTasksApi = GoogleTasksNetwork.api,
) : GoogleTasksRepository {

    override suspend fun tasksOn(date: LocalDate): List<CalendarTask> = withContext(Dispatchers.IO) {
        try {
            val taskLists = api.taskLists().items
            LogUtils.d(
                "taskLists count=${taskLists.size} items=" +
                    taskLists.joinToString { "[id=${it.id}, title=${it.title}]" },
                TAG,
            )
            taskLists
                .mapNotNull { it.id }
                .flatMap { listId -> api.tasks(listId).items.mapNotNull { it.toDomain(listId) } }
                .filter { it.due == date }
                // 未完成在前、已完成在后，同组按标题排序。
                .sortedWith(compareBy({ it.isCompleted }, { it.title }))
        } catch (e: HttpException) {
            // 打印 Google 返回的真实原因，便于区分「Tasks API 未启用」vs「scope 不足」等。
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            LogUtils.w("tasks fetch failed http=${e.code()} body=$body", e, TAG)
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
            LogUtils.d("updateTask ok: listId=$listId taskId=$taskId due=$due", TAG)
        } catch (e: HttpException) {
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            LogUtils.w("updateTask failed http=${e.code()} body=$body", e, TAG)
            throw e
        }
    }

    override suspend fun completeTask(listId: String, taskId: String) = withContext(Dispatchers.IO) {
        try {
            api.patchTask(listId, taskId, TaskPatchDto(status = "completed"))
            LogUtils.d("completeTask ok: listId=$listId taskId=$taskId", TAG)
        } catch (e: HttpException) {
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            LogUtils.w("completeTask failed http=${e.code()} body=$body", e, TAG)
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
                LogUtils.d("createTask ok: id=${created.id} title=$title due=$due", TAG)
            } catch (e: HttpException) {
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                LogUtils.w("createTask failed http=${e.code()} body=$body", e, TAG)
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
