package com.novamind.app.feature.home

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.R
import com.novamind.app.common.config.AppConfig
import com.novamind.app.common.google.GoogleCalendarAuthSource
import com.novamind.app.common.google.GoogleTokenProvider
import com.novamind.app.common.google.TokenOutcome
import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.fold
import com.novamind.app.common.session.UserSessionManager
import com.novamind.app.data.AttachmentsRepository
import com.novamind.app.data.RemoteNoteRepository
import com.novamind.app.data.calendar.TodayAgendaUseCase
import com.novamind.app.data.calendar.isPast
import com.novamind.app.data.tasks.CalendarTask
import com.novamind.app.data.tasks.GoogleTasksRepository
import com.novamind.app.feature.calendar.CalendarBindingStore
import com.novamind.app.feature.create.editor.NoteDocument
import com.novamind.app.feature.create.model.NoteItem
import com.novamind.app.feature.create.model.RemoteNoteSummary
import com.novamind.app.util.ColorUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val notesRepository: RemoteNoteRepository,
    private val attachmentsRepository: AttachmentsRepository,
    private val todayAgenda: TodayAgendaUseCase,
    private val tasksRepository: GoogleTasksRepository,
    private val authSource: GoogleCalendarAuthSource,
    private val bindingStore: CalendarBindingStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    /** 需要用户同意授权时发出恢复意图；Route 收集后用 ActivityResult 启动（与日历页一致）。 */
    private val _consentRequest = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val consentRequest = _consentRequest.asSharedFlow()

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
                    resolveThumbnails(items)
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
            val agenda = todayAgenda()   // 静默授权 + 拉今日会议/任务（尽力而为，失败返回空）
            val items = buildList {
                // 会议（有时间，按开始时间；仓库已按 start 排序）
                agenda.events.filterNot { it.isPast }.forEach { e ->
                    add(
                        UpcomingItem(
                            id = "evt_${e.id}",
                            title = e.title,
                            // 描述优先（压掉多余空白/换行），无描述回退地点
                            subtitle = (e.description?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
                                ?: e.location).orEmpty(),
                            iconResId = R.drawable.ic_upcoming_meeting,
                            time = if (e.isAllDay) "" else e.start.format(TIME_FMT),
                            isMeeting = true,   // 会议卡：带 Start notes
                        )
                    )
                }
                // 任务（date-only，无具体时间）
                agenda.tasks.filterNot { it.isCompleted }.forEach { t ->
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
            // 记住展示中的原始任务，供点击进入详情/编辑取回完整字段（listId/notes/due 等）
            _uiState.update {
                it.copy(
                    upcomingItems = items,
                    todayTasks = agenda.tasks.filterNot { t -> t.isCompleted },
                    // 有可连接账号但未静默授权 → Up next 展示「连接日历」入口
                    calendarNeedsAuth = agenda.accountAvailable && !agenda.authorized,
                )
            }
        }
    }

    // ─── Google 日历连接（Up next 未授权时的入口，复用日历页同意流程） ─────────────

    /**
     * 连接 Google 日历：用当前登录账户取 token。需要用户同意时经 [consentRequest] 让
     * Route 启动同意页，返回后调 [onConsentGranted] 重试；成功即建立绑定并刷新 Up next。
     */
    fun connectCalendar() {
        val account = UserSessionManager.current.userKey
        if (account.isNullOrBlank()) {
            AppLog.w(TAG) { "connectCalendar: no login account" }
            return
        }
        if (_uiState.value.calendarConnecting) return
        _uiState.update { it.copy(calendarConnecting = true) }
        viewModelScope.launch { connectWithAccount(account) }
    }

    /** 用户在同意页授权后回调：用登录账户重试取 token。 */
    fun onConsentGranted() {
        val account = UserSessionManager.current.userKey ?: return
        _uiState.update { it.copy(calendarConnecting = true) }
        viewModelScope.launch { connectWithAccount(account) }
    }

    /** 同意被取消 / 失败：结束连接中态。 */
    fun onConsentCancelled() {
        _uiState.update { it.copy(calendarConnecting = false) }
    }

    private suspend fun connectWithAccount(account: String) {
        when (val outcome = authSource.fetchToken(account)) {
            is TokenOutcome.Success -> {
                GoogleTokenProvider.accessToken = outcome.token
                bindingStore.bind(account, UserSessionManager.current.userKey)
                _uiState.update { it.copy(calendarConnecting = false, calendarNeedsAuth = false) }
                loadUpcoming()   // 刷新今日会议/任务
            }
            is TokenOutcome.NeedsConsent -> {
                AppLog.d(TAG) { "connectCalendar needs consent -> request UI" }
                _consentRequest.tryEmit(outcome.recoveryIntent)   // 保持 connecting，等同意结果
            }
            is TokenOutcome.Failure -> {
                AppLog.w(TAG, outcome.error) { "connectCalendar failed" }
                _uiState.update {
                    it.copy(calendarConnecting = false, errorMessage = "Google 授权失败，请重试")
                }
            }
        }
    }

    // ─── Up next 任务编辑（写回 Google Tasks 后刷新 Up next） ─────────────────────

    /** 更新任务标题/描述/截止日。 */
    fun updateTask(task: CalendarTask, title: String, notes: String?, due: LocalDate) {
        viewModelScope.launch {
            runCatching { tasksRepository.updateTask(task.listId, task.id, title, notes, due) }
            loadUpcoming()
        }
    }

    /** 切换完成状态。 */
    fun setTaskCompleted(task: CalendarTask, completed: Boolean) {
        viewModelScope.launch {
            runCatching { tasksRepository.setCompleted(task.listId, task.id, completed) }
            loadUpcoming()
        }
    }

    /** 删除任务。 */
    fun deleteTask(task: CalendarTask) {
        viewModelScope.launch {
            runCatching { tasksRepository.deleteTask(task.listId, task.id) }
            loadUpcoming()
        }
    }

    /** 列表项领域模型 → UI 模型。列表接口不含正文，故 description/tags 留空；正文在打开详情时另拉。 */
    // ── 首页缩略图按需解析 ──────────────────────────────────────────────
    // 列表接口（RemoteNoteSummary）不含图片信息，故按需拉正文取首图：本地文件优先，
    // 失效则用 fileId 换签名下载 URL。结果按 id@updatedAt 缓存，避免回到首页/刷新时重复请求。
    // 注：首屏最多 HOME_RECENT_NOTES_SIZE 条各一次 getNote（并发上限 4），为纯 UI 增强、失败静默。
    private val thumbCache = mutableMapOf<String, String?>()
    private val thumbSemaphore = Semaphore(4)

    private fun resolveThumbnails(items: List<NoteItem>) {
        items.forEach { item ->
            val key = "${item.id}@${item.updatedAt}"
            if (thumbCache.containsKey(key)) {
                thumbCache[key]?.let { applyThumb(item.id, it) }   // 命中缓存直接回填
                return@forEach
            }
            viewModelScope.launch {
                val resolved = thumbSemaphore.withPermit { resolveThumb(item.id) }
                thumbCache[key] = resolved
                if (resolved != null) applyThumb(item.id, resolved)
            }
        }
    }

    private suspend fun resolveThumb(noteId: String): String? {
        val note = when (val r = notesRepository.getNote(noteId)) {
            is ApiResult.Success -> r.data
            else -> null
        } ?: return null
        // 服务端 content 约定为 {"body": <文档 JSON 字符串>}，先解包出正文文档
        val body = runCatching { JSONObject(note.content).optString("body", "") }
            .getOrDefault("").ifBlank { note.content }
        // 本地首图存在 → 直接用本地路径（即时、离线可看）
        NoteDocument.firstImagePath(body)?.let { p ->
            if (withContext(Dispatchers.IO) { File(p).exists() }) return p
        }
        // 本地失效 → 用首图 fileId 换签名下载 URL
        val fid = NoteDocument.firstImageFileId(body) ?: return null
        val atts = when (val r = attachmentsRepository.list(noteId)) {
            is ApiResult.Success -> r.data
            else -> null
        } ?: return null
        return atts.firstOrNull { it.fileId == fid }?.downloadUrl
    }

    private fun applyThumb(id: String, path: String) {
        _uiState.update { s ->
            s.copy(notes = s.notes.map { if (it.id == id && it.imagePath == null) it.copy(imagePath = path) else it })
        }
    }

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
        const val TAG = "Home"
        val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
