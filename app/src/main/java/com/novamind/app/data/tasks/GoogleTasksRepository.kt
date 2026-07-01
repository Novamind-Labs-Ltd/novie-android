package com.novamind.app.data.tasks

import com.novamind.app.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.time.LocalDate

/**
 * Google Tasks 数据仓库（只读）。授权 token 由 [com.novamind.app.common.google.GoogleTokenProvider] 提供，
 * scope 需含 `tasks.readonly`。
 */
interface GoogleTasksRepository {
    /** 拉取截止日期为 [date] 的任务（跨所有任务列表）。 */
    suspend fun tasksOn(date: LocalDate): List<CalendarTask>
}

class GoogleTasksRepositoryImpl(
    private val api: GoogleTasksApi = GoogleTasksNetwork.api,
) : GoogleTasksRepository {

    override suspend fun tasksOn(date: LocalDate): List<CalendarTask> = withContext(Dispatchers.IO) {
        try {
            api.taskLists().items
                .mapNotNull { it.id }
                .flatMap { listId -> api.tasks(listId).items }
                .mapNotNull { it.toDomain() }
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

    private fun TaskDto.toDomain(): CalendarTask? {
        val id = id ?: return null
        return CalendarTask(
            id = id,
            title = title?.takeIf { it.isNotBlank() } ?: "(No title)",
            // due 为 RFC3339，但仅日期有意义：取日期部分。
            due = due?.substringBefore("T")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            isCompleted = status == "completed",
            notes = notes?.takeIf { it.isNotBlank() },
        )
    }

    private companion object {
        const val TAG = "CalendarTasks"
    }
}
