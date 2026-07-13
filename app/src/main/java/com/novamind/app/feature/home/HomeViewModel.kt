package com.novamind.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.R
import com.novamind.app.common.config.AppConfig
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.data.NotesRepository
import com.novamind.app.feature.create.model.NoteItem
import com.novamind.app.feature.create.model.RemoteNoteSummary
import com.novamind.app.util.ColorUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            upcomingItems = listOf(
                UpcomingItem(
                    id = "1",
                    title = "Monthly report sharing",
                    subtitle = "Team project progress tracking",
                    iconResId = R.drawable.ic_upcoming_report,
                ),
                UpcomingItem(
                    id = "2",
                    title = "Board meeting",
                    subtitle = "Internal stakeholder alignment",
                    iconResId = R.drawable.ic_upcoming_meeting,
                ),
            ),
        )
    )
    val uiState = _uiState.asStateFlow()

    fun onSearchQueryChange(query: String) {
        // TODO: filter
    }

    /**
     * 静默重拉列表：每次回到首页（HomeRoute 重新进入组合）时调用。
     * 不显示下拉刷新指示；已有列表在成功前保留，避免闪空。进行中则跳过，避免并发重复请求。
     */
    fun reload() {
        if (_uiState.value.isLoading) return
        loadNotes(isRefresh = false)
    }

    /** 下拉刷新：重新拉取云端笔记列表。 */
    fun onRefresh() {
        if (_uiState.value.isRefreshing) return
        loadNotes(isRefresh = true)
    }

    /**
     * 拉取云端笔记列表（活跃视图）：GET /api/v1.0/notes。
     * 经 [NotesRepository.listNotes] 走统一 [ApiResult] 三态：成功映射为 UI 列表；
     * 业务错误 / 网络错误落到 [HomeUiState.errorMessage]，并保留已有列表不清空。
     */
    private fun loadNotes(isRefresh: Boolean) {
        _uiState.update {
            if (isRefresh) it.copy(isRefreshing = true, errorMessage = null)
            else it.copy(isLoading = true, errorMessage = null)
        }
        viewModelScope.launch {
            when (val result = notesRepository.listNotes(trashed = false, limit = AppConfig.Paging.NOTES_PAGE_SIZE)) {
                is ApiResult.Success -> {
                    val items = result.data?.items.orEmpty().map { it.toNoteItem() }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            notes = items,
                            errorMessage = null,
                        )
                    }
                }
                is ApiResult.BizError -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.message ?: "Failed to load notes (${result.code})",
                    )
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = "网络异常，请重试",
                    )
                }
            }
        }
    }

    /** 列表项领域模型 → UI 模型。列表接口不含正文，故 description/tags 留空；正文在打开详情时另拉。 */
    private fun RemoteNoteSummary.toNoteItem(): NoteItem = NoteItem(
        id = id,
        title = title.orEmpty(),
        description = "",
        tags = emptyList(),
        borderColor = ColorUtils.parseHexColor(borderColorHex),
        createdAt = createdAt.toEpochMillisOrZero(),
        updatedAt = updatedAt.toEpochMillisOrZero(),
    )

    /** ISO-8601 → epoch 毫秒；空或解析失败回退 0。 */
    private fun String?.toEpochMillisOrZero(): Long =
        this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
}
