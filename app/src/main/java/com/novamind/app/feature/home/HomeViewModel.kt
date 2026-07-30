package com.novamind.app.feature.home

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.R
import com.novamind.app.common.config.AppConfig
import com.novamind.app.common.google.GoogleTokenProvider
import com.novamind.app.common.log.AppLog
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.fold
import com.novamind.app.common.net.response.getOrNull
import com.novamind.app.common.session.UserSessionManager
import com.novamind.app.data.AttachmentsRepository
import com.novamind.app.data.RemoteNoteRepository
import com.novamind.app.data.calendar.CalendarEvent
import com.novamind.app.data.calendar.TodayAgenda
import com.novamind.app.data.calendar.TodayAgendaUseCase
import com.novamind.app.data.calendar.CalendarNoteRepository
import com.novamind.app.data.calendar.GoogleCalendarRepository
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private val calendarRepository: GoogleCalendarRepository,
    private val bindingStore: CalendarBindingStore,
    private val calendarNoteRepository: CalendarNoteRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()
    private val _openNote = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openNote = _openNote.asSharedFlow()
    private val meetingNoteActions = mutableSetOf<String>()
    private var notesRequested = false
    private var lastNoteRefreshRequestId = 0L

    fun onSearchQueryChange(query: String) {
        // TODO: filter
    }

    /**
     * Home 进入组合时调用。笔记首次进入加载一次；只有编辑页返回时递增的
     * [noteRefreshRequestId] 才会再次触发笔记请求，普通二级页返回不会重拉笔记。
     * 日历仍在每次回到 Home 时刷新，避免 Up next 长时间使用旧数据。
     */
    fun onHomeEntered(noteRefreshRequestId: Long) {
        val shouldLoadNotes = !notesRequested || noteRefreshRequestId > lastNoteRefreshRequestId
        if (shouldLoadNotes) {
            notesRequested = true
            lastNoteRefreshRequestId = noteRefreshRequestId
            if (!_uiState.value.isLoading) loadNotes(isRefresh = false)
        }
        loadUpcoming()
    }

    /** 下拉刷新：重新拉取云端笔记与今日日历。 */
    fun onRefresh() {
        // 首屏请求尚未完成时不再并发发起一套刷新请求，避免两个响应按不同顺序覆盖页面。
        if (_uiState.value.isRefreshing || _uiState.value.isLoading) return
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }

        viewModelScope.launch {
            // 笔记与 Up next 并行请求，但等两者都完成后再结束 isRefreshing，
            // 这样下拉容器回弹时页面内容高度已经稳定。
            val (notesResult, agenda) = coroutineScope {
                val notesRequest = async {
                    notesRepository.listNotes(
                        trashed = false,
                        limit = AppConfig.Paging.HOME_RECENT_NOTES_SIZE,
                    )
                }
                val agendaRequest = async {
                    try {
                        todayAgenda()
                    } catch (c: CancellationException) {
                        throw c
                    } catch (_: Exception) {
                        TodayAgenda()
                    }
                }
                notesRequest.await() to agendaRequest.await()
            }

            notesResult.fold(
                onSuccess = { page ->
                    val previousById = _uiState.value.notes.associateBy { it.id }
                    val items = page?.items.orEmpty().map { summary ->
                        val fresh = summary.toNoteItem()
                        val previous = previousById[fresh.id]
                        if (previous == null) {
                            fresh
                        } else {
                            // 刷新接口暂时没有缩略图时保留旧图，避免卡片先缩短、再被详情图撑高。
                            fresh.copy(
                                imagePath = fresh.imagePath ?: previous.imagePath,
                                borderColor = fresh.borderColor ?: previous.borderColor,
                            )
                        }
                    }
                    _uiState.update {
                        it.copy(notes = items, errorMessage = null)
                    }
                    resolveNoteExtras(items)
                },
                onFail = { result ->
                    val message = when (result) {
                        is ApiResult.BizError -> result.message ?: "Failed to load notes (${result.code})"
                        else -> "网络异常，请重试"
                    }
                    _uiState.update { it.copy(errorMessage = message) }
                },
            )

            applyAgenda(agenda)
            _uiState.update { it.copy(isRefreshing = false) }
        }
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
                    resolveNoteExtras(items)
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
     * 拉取今日 Up next：仅展示 Google 日历中今日尚未结束的会议（[isPast] 为 false）。
     * Google Tasks 仍由共享议程用例读取，但不映射到首页 Up next；任务请从日历页查看。
     * 尽力而为：未连接 Google / 授权过期 / 网络错误都视为「无数据」，交由 UI 显示空状态。
     */
    private fun loadUpcoming() {
        viewModelScope.launch {
            applyAgenda(todayAgenda())
        }
    }

    /** Upcoming 页面进入时拉取未来两周（今天含、14 天后不含）的 Google Calendar 会议。 */
    fun loadUpcomingRange() {
        if (_uiState.value.upcomingRangeLoading || _uiState.value.upcomingRangeRefreshing) return
        _uiState.update { it.copy(upcomingRangeLoading = true) }
        viewModelScope.launch {
            applyRangeAgenda(fetchUpcomingRangeAgenda())
        }
    }

    /** Upcoming 页面下拉刷新：保留现有列表，刷新完成后一次性替换。 */
    fun refreshUpcomingRange() {
        if (_uiState.value.upcomingRangeLoading || _uiState.value.upcomingRangeRefreshing) return
        _uiState.update { it.copy(upcomingRangeRefreshing = true) }
        viewModelScope.launch {
            applyRangeAgenda(fetchUpcomingRangeAgenda())
            _uiState.update { it.copy(upcomingRangeRefreshing = false) }
        }
    }

    private suspend fun fetchUpcomingRangeAgenda(): TodayAgenda {
        val start = LocalDate.now()
        return try {
            todayAgenda.eventsBetween(start, start.plusDays(14))
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            TodayAgenda()
        }
    }

    /** 将同一份议程结果一次性映射到 Up next，避免刷新期间分段改变页面高度。 */
    private suspend fun applyAgenda(agenda: TodayAgenda) {
        val events = agenda.events.filterNot { it.isPast }
        val linkedNoteIds = if (agenda.authorized && events.isNotEmpty()) {
            firstMeetingNoteId(events)
        } else {
            emptyMap()
        }
        val items = buildList {
            // 会议（有时间，按开始时间；仓库已按 start 排序）
            events.forEach { e ->
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
                        date = e.start.toLocalDate(),
                        isAllDay = e.isAllDay,
                        noteId = linkedNoteIds[e.id],
                    )
                )
            }
        }
        // 保留今日任务原始数据，供后续需要时使用；首页 Up next 不展示任务卡。
        _uiState.update {
            it.copy(
                upcomingItems = items,
                upcomingEvents = events,
                todayTasks = agenda.tasks.filterNot { t -> t.isCompleted },
                // 有可连接账号但未静默授权 → Up next 展示「连接日历」入口
                calendarNeedsAuth = agenda.accountAvailable && !agenda.authorized,
            )
        }
    }

    /** 已有关联直接打开；未关联则先创建笔记，再绑定 calendarId，最后进入笔记页。 */
    fun openMeetingNote(eventId: String, noteId: String?) {
        if (noteId != null) {
            _openNote.tryEmit(noteId)
            return
        }
        if (!meetingNoteActions.add(eventId)) return
        val meetingTitle = (
            _uiState.value.upcomingEvents.firstOrNull { event -> event.id == eventId }?.title
                ?: _uiState.value.upcomingRangeEvents.firstOrNull { event -> event.id == eventId }?.title
            )?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            try {
                when (val createResult = notesRepository.createNote(title = meetingTitle, body = "")) {
                    is ApiResult.Success -> {
                        val createdNoteId = createResult.data?.id
                        if (createdNoteId == null) {
                            _uiState.update { it.copy(errorMessage = "Failed to create meeting notes") }
                            return@launch
                        }
                        when (val bindResult = calendarNoteRepository.bindNote(eventId, createdNoteId)) {
                            is ApiResult.Success -> {
                                val resolvedNoteId = bindResult.data ?: createdNoteId
                                _uiState.update { state ->
                                    state.copy(
                                        upcomingItems = state.upcomingItems.map { item ->
                                            if (item.id == "evt_$eventId") item.copy(noteId = resolvedNoteId) else item
                                        },
                                        upcomingRangeItems = state.upcomingRangeItems.map { item ->
                                            if (item.id == "evt_$eventId") item.copy(noteId = resolvedNoteId) else item
                                        },
                                    )
                                }
                                _openNote.emit(resolvedNoteId)
                            }
                            is ApiResult.BizError -> _uiState.update {
                                it.copy(errorMessage = bindResult.message ?: "Failed to bind meeting notes")
                            }
                            is ApiResult.NetworkError -> _uiState.update {
                                it.copy(errorMessage = "网络异常，请重试")
                            }
                        }
                    }
                    is ApiResult.BizError -> _uiState.update {
                        it.copy(errorMessage = createResult.message ?: "Failed to create meeting notes")
                    }
                    is ApiResult.NetworkError -> _uiState.update {
                        it.copy(errorMessage = "网络异常，请重试")
                    }
                }
            } finally {
                meetingNoteActions.remove(eventId)
            }
        }
    }

    private suspend fun applyRangeAgenda(agenda: TodayAgenda) {
        val start = LocalDate.now()
        val endExclusive = start.plusDays(14)
        val events = agenda.events
            .asSequence()
            .filterNot { it.isPast }
            .filter { event ->
                val date = event.start.toLocalDate()
                !date.isBefore(start) && date.isBefore(endExclusive)
            }
            .sortedBy { it.start }
            .toList()
        val linkedNoteIds = if (agenda.authorized && events.isNotEmpty()) {
            firstMeetingNoteId(events)
        } else {
            emptyMap()
        }
        val items = events
            .map { event ->
                UpcomingItem(
                    id = "evt_${event.id}",
                    title = event.title,
                    subtitle = (event.description?.replace(Regex("\\s+"), " ")?.trim()
                        ?.takeIf { it.isNotEmpty() } ?: event.location).orEmpty(),
                    iconResId = R.drawable.ic_upcoming_meeting,
                    time = if (event.isAllDay) "" else event.start.format(TIME_FMT),
                    isMeeting = true,
                    date = event.start.toLocalDate(),
                    isAllDay = event.isAllDay,
                    noteId = linkedNoteIds[event.id],
                )
            }
            .toList()

        _uiState.update {
            it.copy(
                upcomingRangeItems = items,
                upcomingRangeEvents = events,
                upcomingRangeLoading = false,
                calendarNeedsAuth = agenda.accountAvailable && !agenda.authorized,
            )
        }
    }

    /** 只有第一张会议卡展示笔记动作，因此仅查询第一场会议的绑定状态。 */
    private suspend fun firstMeetingNoteId(events: List<CalendarEvent>): Map<String, String> {
        val firstEvent = events.firstOrNull() ?: return emptyMap()
        val noteId = calendarNoteRepository.getNoteId(firstEvent.id).getOrNull() ?: return emptyMap()
        return mapOf(firstEvent.id to noteId)
    }

    // ─── Google 日历连接（Up next 未授权时的入口，复用日历页同意流程） ─────────────

    /** Route 即将启动 Google 账号选择时切换按钮 loading。 */
    fun beginCalendarConnection() {
        if (_uiState.value.calendarConnecting) return
        _uiState.update { it.copy(calendarConnecting = true) }
    }

    /** 账号选择被取消 / 失败：结束连接中态。 */
    fun onConsentCancelled() {
        _uiState.update { it.copy(calendarConnecting = false) }
    }

    /** 交互式 Google 授权成功后绑定真实 Google 邮箱并刷新 Up next。 */
    fun onCalendarTokenObtained(token: String) {
        GoogleTokenProvider.accessToken = token
        viewModelScope.launch {
            runCatching { calendarRepository.currentAccountEmail() }
                .onSuccess { accountEmail ->
                    bindingStore.bind(accountEmail, UserSessionManager.current.userKey)
                    _uiState.update { it.copy(calendarConnecting = false, calendarNeedsAuth = false) }
                    loadUpcoming()
                    loadUpcomingRange()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    GoogleTokenProvider.clear()
                    AppLog.w(TAG, error) { "connectCalendar: resolve Google account failed" }
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

    // ── 首页笔记缩略图「兜底」按需解析 ────────────────────────────────────
    // 边框色与缩略图现已随列表接口（RemoteNoteSummary.borderColorHex / thumbnailUrl）直接返回，
    // 一般无需再拉详情。此处仅为**兜底**：列表未给缩略图（thumbnailUrl=null，如缩略图尚在生成）时，
    // 拉一次 getNote 用正文首图解析（本地文件优先，失效则用 fileId 换签名 URL）。
    // 因此只对 imagePath 仍为空的条目发起请求，避免每次进首页 N+1 次 getNote。
    // 结果按 id@updatedAt 缓存（改动会 bump updatedAt → 自动失效），纯 UI 增强、失败静默。
    private data class NoteExtras(val imagePath: String?, val borderColor: Color?)
    private val extrasCache = mutableMapOf<String, NoteExtras>()
    private val thumbSemaphore = Semaphore(4)

    private fun resolveNoteExtras(items: List<NoteItem>) {
        // 列表已带缩略图的条目跳过（imagePath 非空即来自 thumbnailUrl）。
        items.filter { it.imagePath == null }.forEach { item ->
            val key = "${item.id}@${item.updatedAt}"
            extrasCache[key]?.let { applyExtras(item.id, it); return@forEach }   // 命中缓存直接回填
            viewModelScope.launch {
                val extras = thumbSemaphore.withPermit { fetchNoteExtras(item.id) } ?: NoteExtras(null, null)
                extrasCache[key] = extras
                applyExtras(item.id, extras)
            }
        }
    }

    private suspend fun fetchNoteExtras(noteId: String): NoteExtras? {
        val note = when (val r = notesRepository.getNote(noteId)) {
            is ApiResult.Success -> r.data
            else -> null
        } ?: return null
        val border = ColorUtils.parseHexColor(note.borderColorHex)
        // 服务端 content 约定为 {"body": <文档 JSON 字符串>}，先解包出正文文档
        val body = runCatching { JSONObject(note.content).optString("body", "") }
            .getOrDefault("").ifBlank { note.content }
        val imagePath = resolveThumb(noteId, body)
        return NoteExtras(imagePath, border)
    }

    private suspend fun resolveThumb(noteId: String, body: String): String? {
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

    private fun applyExtras(id: String, extras: NoteExtras) {
        _uiState.update { s ->
            s.copy(
                notes = s.notes.map {
                    if (it.id == id) {
                        it.copy(
                            imagePath = it.imagePath ?: extras.imagePath,
                            borderColor = it.borderColor ?: extras.borderColor,
                        )
                    } else {
                        it
                    }
                },
            )
        }
    }

    private fun RemoteNoteSummary.toNoteItem(): NoteItem = NoteItem(
        id = id,
        title = title.orEmpty(),
        preview = preview.orEmpty(),
        tags = emptyList(),
        borderColor = ColorUtils.parseHexColor(borderColorHex),
        // 列表接口已直接返回缩略图签名 URL（http…），卡片 AsyncImage 直接加载，无需再取详情。
        imagePath = thumbnailUrl,
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
