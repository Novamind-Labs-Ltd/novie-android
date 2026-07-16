package com.novamind.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.R
import com.novamind.app.common.config.AppConfig
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.fold
import com.novamind.app.data.RemoteNoteRepository
import com.novamind.app.data.calendar.GoogleCalendarRepository
import com.novamind.app.data.calendar.isPast
import com.novamind.app.data.tasks.GoogleTasksRepository
import com.novamind.app.feature.create.model.NoteItem
import com.novamind.app.feature.create.model.RemoteNoteSummary
import com.novamind.app.util.ColorUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val notesRepository: RemoteNoteRepository,
    private val calendarRepository: GoogleCalendarRepository,
    private val tasksRepository: GoogleTasksRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    fun onSearchQueryChange(query: String) {
        // TODO: filter
    }

    /**
     * 静默重拉：每次回到首页（HomeRoute 重新进入组合）时调用。
     * 同时刷新云端笔记与今日日历（会议/任务）；进行中则跳过笔记重复请求。
     */
    fun reload() {
        if (!_uiState.value.isLoading) loadNotes(isRefresh = false)
        loadUpcoming()
    }

    /** 下拉刷新：重新拉取云端笔记与今日日历。 */
    fun onRefresh() {
        if (_uiState.value.isRefreshing) return
        loadNotes(isRefresh = true)
        loadUpcoming()
    }

    /**
     * 拉取云端笔记列表（活跃视图）：GET /api/v1.0/notes。
     * 经 [RemoteNoteRepository.listNotes] 走统一 [ApiResult] 三态：成功映射为 UI 列表；
     * 业务错误 / 网络错误落到 [HomeUiState.errorMessage]，并保留已有列表不清空。
     */
    private fun loadNotes(isRefresh: Boolean) {
        _uiState.update {
            if (isRefresh) it.copy(isRefreshing = true, errorMessage = null)
            else it.copy(isLoading = true, errorMessage = null)
        }
        viewModelScope.launch {
            notesRepository.listNotes(trashed = false, limit = AppConfig.Paging.HOME_RECENT_NOTES_SIZE).fold(
                onSuccess = { page ->
                    val items = page?.items.orEmpty().map { it.toNoteItem() }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            notes = items,
                            errorMessage = null,
                        )
                    }
                },
                onFail = { result ->
                    val message = when (result) {
                        is ApiResult.BizError -> result.message ?: "Failed to load notes (${result.code})"
                        else -> "网络异常，请重试"
                    }
                    _uiState.update {
                        it.copy(isLoading = false, isRefreshing = false, errorMessage = message)
                    }
                },
            )
        }
    }

    /**
     * 拉取今日 Up next：Google 日历「会议」+ Google Tasks「任务」，仅取当天数据。
     * - 会议：今日主日历事件中尚未结束的（[isPast] 为 false）；
     * - 任务：今日截止且未完成的。
     * 尽力而为：未连接 Google / 授权过期 / 网络错误都视为「无数据」，交由 UI 显示空状态。
     */
    private fun loadUpcoming() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val events = safeFetch { calendarRepository.eventsOn(today) }
            val tasks = safeFetch { tasksRepository.tasksOn(today) }

            val items = buildList {
                // 会议（有时间，按开始时间；仓库已按 start 排序）
                events.filterNot { it.isPast }.forEach { e ->
                    add(
                        UpcomingItem(
                            id = "evt_${e.id}",
                            title = e.title,
                            subtitle = e.location.orEmpty(),
                            iconResId = R.drawable.ic_upcoming_meeting,
                            time = if (e.isAllDay) "" else e.start.format(TIME_FMT),
                            isMeeting = true,   // 会议卡：带 Start notes
                        )
                    )
                }
                // 任务（date-only，无具体时间）
                tasks.filterNot { it.isCompleted }.forEach { t ->
                    add(
                        UpcomingItem(
                            id = "task_${t.id}",
                            title = t.title,
                            subtitle = t.notes.orEmpty(),
                            iconResId = R.drawable.ic_upcoming_report,
                            time = "",
                        )
                    )
                }
            }
            _uiState.update { it.copy(upcomingItems = items) }
        }
    }

    /** best-effort 拉取：吞掉业务/网络异常返回空，但不吞协程取消。 */
    private suspend fun <T> safeFetch(block: suspend () -> List<T>): List<T> =
        try {
            block()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            emptyList()
        }

    /** 列表项领域模型 → UI 模型。列表接口不含正文，故 description/tags 留空；正文在打开详情时另拉。 */
    private fun RemoteNoteSummary.toNoteItem(): NoteItem = NoteItem(
        id = id,
        title = title.orEmpty(),
        preview = preview.orEmpty(),
        tags = emptyList(),
        borderColor = ColorUtils.parseHexColor(borderColorHex),
        createdAt = createdAt.toEpochMillisOrZero(),
        updatedAt = updatedAt.toEpochMillisOrZero(),
    )

    /** ISO-8601 → epoch 毫秒；空或解析失败回退 0。 */
    private fun String?.toEpochMillisOrZero(): Long =
        this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

    private companion object {
        val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
