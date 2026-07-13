package com.novamind.app.feature.recyclebin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.common.config.AppConfig
import com.novamind.app.common.log.AppLog
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

/**
 * 回收站 ViewModel：**由服务端驱动**。列表拉 `GET /notes?trashed=true`；
 * 恢复走 `PATCH {trashed:false}`，彻底删除走 `DELETE /notes/{id}`，成功后重拉列表。
 * 列表项复用 [NoteItem]，其 updatedAt 映射为软删时间（deletedAt），用于计算「剩余 N 天」。
 */
@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecycleBinUiState())
    val uiState = _uiState.asStateFlow()

    init {
        reload()
    }

    /** 拉取回收站笔记（GET /notes?trashed=true）。 */
    fun reload() {
        viewModelScope.launch {
            when (val r = notesRepository.listNotes(trashed = true, limit = AppConfig.Paging.NOTES_PAGE_SIZE)) {
                is ApiResult.Success -> {
                    val items = r.data?.items.orEmpty().map { it.toNoteItem() }
                    _uiState.update { it.copy(notes = items) }
                }
                is ApiResult.BizError -> AppLog.w(TAG) { "loadTrashed 业务错误 code=${r.code} traceId=${r.traceId}" }
                is ApiResult.NetworkError -> AppLog.w(TAG) { "loadTrashed 网络错误: ${r.message}" }
            }
        }
    }

    /** 从回收站恢复笔记（PATCH {trashed:false}），成功后重拉。 */
    fun restore(noteId: String) {
        viewModelScope.launch {
            when (val r = notesRepository.setTrashed(noteId, trashed = false)) {
                is ApiResult.Success -> reload()
                is ApiResult.BizError -> AppLog.w(TAG) { "restore 业务错误 id=$noteId code=${r.code} traceId=${r.traceId}" }
                is ApiResult.NetworkError -> AppLog.w(TAG) { "restore 网络错误 id=$noteId: ${r.message}" }
            }
        }
    }

    /** 彻底删除（DELETE，不可恢复），成功后重拉。 */
    fun deleteForever(noteId: String) {
        viewModelScope.launch {
            when (val r = notesRepository.deleteNote(noteId)) {
                is ApiResult.Success -> reload()
                is ApiResult.BizError -> AppLog.w(TAG) { "deleteForever 业务错误 id=$noteId code=${r.code} traceId=${r.traceId}" }
                is ApiResult.NetworkError -> AppLog.w(TAG) { "deleteForever 网络错误 id=$noteId: ${r.message}" }
            }
        }
    }

    /** 列表项领域模型 → UI 模型。列表接口不含正文，故 description/tags/图片留空；updatedAt 用软删时间。 */
    private fun RemoteNoteSummary.toNoteItem(): NoteItem = NoteItem(
        id = id,
        title = title.orEmpty(),
        description = "",
        tags = emptyList(),
        borderColor = ColorUtils.parseHexColor(borderColorHex),
        folderName = null,
        createdAt = createdAt.toEpochMillisOrZero(),
        updatedAt = deletedAt.toEpochMillisOrZero(),   // 软删时间，用于「剩余 N 天」倒计时
    )

    /** ISO-8601 → epoch 毫秒；空或解析失败回退 0。 */
    private fun String?.toEpochMillisOrZero(): Long =
        this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

    private companion object {
        const val TAG = "RecycleBinVM"
    }
}
