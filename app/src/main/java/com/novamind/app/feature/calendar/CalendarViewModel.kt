package com.novamind.app.feature.calendar

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.NovieApplication
import com.novamind.app.common.google.GoogleAccount
import com.novamind.app.common.google.GoogleCalendarAuthSource
import com.novamind.app.common.google.GoogleTokenProvider
import com.novamind.app.common.google.TokenOutcome
import com.novamind.app.common.session.AppUserProvider
import com.novamind.app.data.calendar.CalendarEventCache
import com.novamind.app.data.calendar.GoogleAuthExpiredException
import com.novamind.app.data.calendar.GoogleAuthRevokedException
import com.novamind.app.data.calendar.GoogleCalendarRepository
import com.novamind.app.data.tasks.CalendarTask
import com.novamind.app.data.tasks.GoogleTasksRepository
import com.novamind.app.util.LogUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Calendar 页面 ViewModel：以 [CalendarConnectionStatus] 状态机驱动 UI。
 *
 * 设计见 calendar-connection-design.md。职责：
 * - 进入页面尝试**静默续期**（已授权则无感刷新，不弹 UI）；
 * - 按日期拉取事件，先渲染按账号隔离的缓存再后台刷新；
 * - 编排断开 / 换账号 / 重试，并把鉴权错误分流到对应状态。
 *
 * 需 Activity 的交互式授权（选账号/同意）仍在 [CalendarRoute]；本类只用
 * [GoogleCalendarAuthSource] 做静默授权与 revoke。
 */
class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NovieApplication
    private val repository: GoogleCalendarRepository = app.googleCalendarRepository
    private val tasksRepository: GoogleTasksRepository = app.googleTasksRepository
    private val bindingStore: CalendarBindingStore = app.calendarBindingStore
    private val eventCache: CalendarEventCache = app.calendarEventCache
    private val authSource: GoogleCalendarAuthSource = app.googleCalendarAuthSource

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState = _uiState.asStateFlow()

    /** 需要用户同意时，发出恢复授权 Intent；Route 收集后用 ActivityResult 启动。 */
    private val _consentRequest = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val consentRequest = _consentRequest.asSharedFlow()

    init {
        // 响应式联动 App 会话：登录/登出/游客切换时重新评估，避免 VM 被保留后状态停滞
        // （如游客进过日历页后登录，仍显示「登录后使用」）。StateFlow 会立即发射当前值。
        viewModelScope.launch {
            AppUserProvider.session.collect { refreshAuthAndLoad() }
        }
    }

    fun onEvent(event: CalendarUiEvent) {
        when (event) {
            // Connect / SwitchAccount 需 Activity，由 Route 拦截编排，VM 不处理。
            CalendarUiEvent.Connect -> Unit
            CalendarUiEvent.SwitchAccount -> Unit
            is CalendarUiEvent.AuthFailed -> _uiState.update {
                it.copy(errorMessage = event.message ?: "Google authorization failed")
            }
            CalendarUiEvent.Disconnect -> onDisconnect()
            CalendarUiEvent.Retry -> loadEvents()
            is CalendarUiEvent.SelectAgendaFilter -> _uiState.update {
                // 再次点击已选过滤 → 回到全部。
                it.copy(filter = if (it.filter == event.filter) AgendaFilter.ALL else event.filter)
            }
            is CalendarUiEvent.CompleteTask -> completeTask(event.task)
            is CalendarUiEvent.DateSelected -> selectDate(event.date)
            CalendarUiEvent.PrevDay -> selectDate(_uiState.value.selectedDate.minusDays(1))
            CalendarUiEvent.NextDay -> selectDate(_uiState.value.selectedDate.plusDays(1))
            CalendarUiEvent.Refresh -> if (_uiState.value.isConnected) loadEvents()
            CalendarUiEvent.ErrorShown -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    /**
     * 进入页面 / 冷启动的入口。
     * - 已有绑定：一致性校验后用绑定账号静默续期（拿不到 token → [CalendarConnectionStatus.PERMISSION_REVOKED]）。
     * - 无绑定但已登录：静默探测当前登录账户是否**已授权日历读取**——已授权则**自动连接**并建立绑定，
     *   未授权才显示首次连接卡片（[CalendarConnectionStatus.NOT_CONNECTED]）。
     */
    private fun refreshAuthAndLoad() {
        // 游客（免登录）不可用日历：拦截为「登录后使用」，不触发任何授权/拉取。
        if (AppUserProvider.isGuest) {
            LogUtils.d("refreshAuthAndLoad: guest -> LOGIN_REQUIRED", TAG)
            _uiState.update { CalendarUiState(connectionStatus = CalendarConnectionStatus.LOGIN_REQUIRED) }
            return
        }
        val currentUser = AppUserProvider.currentUserKey

        if (bindingStore.isConnected) {
            // 软一致性：仅当**已知**当前登录用户、且与绑定不一致（确实换了账号）时才清日历。
            // currentUser 为 null 多是冷启动认证层尚未写回登录态的瞬态——此时不清绑定，
            // 否则会误删、导致已授权用户每次冷启动都要重连；等会话就绪会再次触发本方法。
            if (currentUser != null && bindingStore.appUserKey != currentUser) {
                LogUtils.d("refreshAuthAndLoad: app user mismatch -> clear calendar", TAG)
                clearLocalSession()
                _uiState.update { CalendarUiState(selectedDate = it.selectedDate) }
                return
            }
            connectSilently(bindingStore.accountEmail, isReconnect = true)
            return
        }

        // 无绑定：未登录（或登录态未就绪）→ 先显示连接卡片，待会话就绪再触发。
        if (currentUser.isNullOrBlank()) {
            LogUtils.d("refreshAuthAndLoad: no binding & no login user -> NOT_CONNECTED", TAG)
            _uiState.update { it.copy(connectionStatus = CalendarConnectionStatus.NOT_CONNECTED) }
            return
        }
        // 已登录但无绑定：静默探测是否已授权，已授权则自动连接。
        LogUtils.d("refreshAuthAndLoad: no binding, probe login account $currentUser", TAG)
        connectSilently(currentUser, isReconnect = false)
    }

    /**
     * 用 [accountName] 静默取 token 并落地。
     * @param isReconnect true=已有绑定的续期（失败→PERMISSION_REVOKED）；
     *                    false=无绑定的探测（成功则建立绑定；失败→NOT_CONNECTED 显示连接卡片，不当作错误）。
     */
    private fun connectSilently(accountName: String?, isReconnect: Boolean) {
        viewModelScope.launch {
            val date = _uiState.value.selectedDate
            val cached = accountName?.let { eventCache.get(it, date) }
            _uiState.update {
                it.copy(
                    connectionStatus = CalendarConnectionStatus.SYNCING,
                    account = accountName?.let { e -> GoogleAccount(e) } ?: it.account,
                    events = cached ?: it.events,
                    errorMessage = null,
                )
            }
            if (accountName != null && acquireTokenSilently(accountName)) {
                if (!isReconnect) bindingStore.bind(accountName, AppUserProvider.currentUserKey)
                fetchInto(date, accountName, allowSilentRetry = false)
            } else if (isReconnect) {
                LogUtils.d("connectSilently: reconnect needs consent -> REVOKED", TAG)
                GoogleTokenProvider.clear()
                _uiState.update {
                    it.copy(
                        connectionStatus = CalendarConnectionStatus.PERMISSION_REVOKED,
                        errorMessage = "Google authorization expired, please reconnect",
                    )
                }
            } else {
                LogUtils.d("connectSilently: not yet authorized -> NOT_CONNECTED", TAG)
                GoogleTokenProvider.clear()
                _uiState.update {
                    it.copy(connectionStatus = CalendarConnectionStatus.NOT_CONNECTED, events = emptyList())
                }
            }
        }
    }

    /**
     * 连接日历：直接用**当前 App 登录账户**邮箱取 token，不弹账号选择器（日历账户跟随登录账户）。
     * 需要用户同意时，通过 [consentRequest] 让 Route 启动恢复意图，返回后调 [onConsentGranted] 重试。
     */
    fun connectWithCurrentAccount() {
        val accountName = AppUserProvider.currentUserKey
        if (accountName.isNullOrBlank()) {
            LogUtils.w("connect: no login account email", tag = TAG)
            _uiState.update {
                it.copy(
                    connectionStatus = CalendarConnectionStatus.SYNC_FAILED,
                    errorMessage = "No login account to connect",
                )
            }
            return
        }
        LogUtils.d("connect with login account: $accountName", TAG)
        _uiState.update {
            it.copy(
                connectionStatus = CalendarConnectionStatus.SYNCING,
                account = GoogleAccount(accountName),
                errorMessage = null,
            )
        }
        viewModelScope.launch { connectWithAccount(accountName) }
    }

    /** 用户在恢复授权页同意后回调：用已选账号重试取 token。 */
    fun onConsentGranted() {
        val accountName = _uiState.value.account?.email ?: return
        _uiState.update { it.copy(connectionStatus = CalendarConnectionStatus.SYNCING, errorMessage = null) }
        viewModelScope.launch { connectWithAccount(accountName) }
    }

    private suspend fun connectWithAccount(accountName: String) {
        when (val outcome = authSource.fetchToken(accountName)) {
            is TokenOutcome.Success -> {
                GoogleTokenProvider.accessToken = outcome.token
                bindingStore.bind(accountName, AppUserProvider.currentUserKey)
                _uiState.update { it.copy(account = GoogleAccount(accountName)) }
                fetchInto(_uiState.value.selectedDate, accountName, allowSilentRetry = false)
            }
            is TokenOutcome.NeedsConsent -> {
                LogUtils.d("connect needs consent -> request UI", TAG)
                _consentRequest.tryEmit(outcome.recoveryIntent)
            }
            is TokenOutcome.Failure -> _uiState.update {
                it.copy(
                    connectionStatus = CalendarConnectionStatus.SYNC_FAILED,
                    errorMessage = connectFailureMessage(outcome.error),
                )
            }
        }
    }

    /**
     * 每次日历页显示（进入/切回 tab/回前台）时调用：
     * - 已连接 → 重新拉取事件（刷新）；
     * - 同步中 → 跳过（已在进行）；
     * - 其余（未连接/撤销/失败/游客）→ 重新评估（含自动连接探测、游客拦截）。
     */
    fun onScreenShown() {
        when (_uiState.value.connectionStatus) {
            CalendarConnectionStatus.SYNCING -> Unit
            CalendarConnectionStatus.CONNECTED -> loadEvents()
            else -> refreshAuthAndLoad()
        }
    }

    /**
     * 右滑完成任务：先乐观置为已完成（未完成在前、已完成在后，与仓库排序一致），
     * 再调 API；失败回滚原列表并提示。已完成的任务忽略（UI 侧也不可滑）。
     */
    private fun completeTask(task: CalendarTask) {
        if (task.isCompleted) return
        val previous = _uiState.value.tasks
        _uiState.update { state ->
            state.copy(
                tasks = state.tasks
                    .map { if (it.id == task.id) it.copy(isCompleted = true) else it }
                    .sortedWith(compareBy({ it.isCompleted }, { it.title })),
            )
        }
        viewModelScope.launch {
            runCatching { tasksRepository.completeTask(task.listId, task.id) }
                .onFailure { e ->
                    LogUtils.w("completeTask failed: id=${task.id}", e, TAG)
                    _uiState.update {
                        it.copy(tasks = previous, errorMessage = "Couldn't complete the task. Please retry.")
                    }
                }
        }
    }

    private fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        if (_uiState.value.isConnected) loadEvents()
    }

    /** 拉取选中日期事件：进入同步态、先渲染缓存，再请求网络。 */
    private fun loadEvents() {
        val date = _uiState.value.selectedDate
        val accountId = _uiState.value.account?.email
        viewModelScope.launch {
            LogUtils.d("loadEvents start: date=$date", TAG)
            val cached = accountId?.let { eventCache.get(it, date) }
            _uiState.update {
                it.copy(
                    connectionStatus = CalendarConnectionStatus.SYNCING,
                    events = cached ?: it.events,
                    errorMessage = null,
                )
            }
            fetchInto(date, accountId, allowSilentRetry = true)
        }
    }

    /**
     * 实际拉取并落地结果。401 时进入 [CalendarConnectionStatus.TOKEN_EXPIRED] 并静默续期一次；
     * 续期失败或 403 → [CalendarConnectionStatus.PERMISSION_REVOKED]；其余 → [CalendarConnectionStatus.SYNC_FAILED]。
     */
    private suspend fun fetchInto(date: LocalDate, accountId: String?, allowSilentRetry: Boolean) {
        // 活动与任务并行拉取；活动的鉴权错误驱动状态，任务为 best-effort（失败不影响活动展示）。
        runCatching {
            coroutineScope {
                val eventsDeferred = async { repository.eventsOn(date) }
                val tasksDeferred = async {
                    runCatching {
                        tasksRepository.tasksOn(date)
                    }
                        .onFailure { LogUtils.w("loadTasks failed: date=$date", it, TAG) }
                        .getOrDefault(emptyList())
                }

                eventsDeferred.await() to tasksDeferred.await()
            }
        }
            .onSuccess { (events, tasks) ->
                LogUtils.d("load success: date=$date, events=${events.size}, tasks=${tasks.size}", TAG)
                events.forEachIndexed { i, e ->
                    LogUtils.d(
                        "  event[$i] id=${e.id} title=${e.title} allDay=${e.isAllDay} " +
                            "start=${e.start} end=${e.end} eventType=${e.eventType} " +
                            "location=${e.location}",
                        TAG,
                    )
                }
                tasks.forEachIndexed { i, t ->
                    LogUtils.d(
                        "  task[$i] id=${t.id} title=${t.title} due=${t.due} " +
                            "completed=${t.isCompleted} notes=${t.notes}",
                        TAG,
                    )
                }
                if (accountId != null) eventCache.put(accountId, date, events)
                _uiState.update {
                    it.copy(
                        connectionStatus = CalendarConnectionStatus.CONNECTED,
                        events = events,
                        tasks = tasks,
                    )
                }
            }
            .onFailure { e ->
                LogUtils.e("loadEvents failure: date=$date", e, TAG)
                when (e) {
                    is GoogleAuthExpiredException -> {
                        _uiState.update { it.copy(connectionStatus = CalendarConnectionStatus.TOKEN_EXPIRED) }
                        // 清掉 GMS 缓存的旧 token，再为同一账号静默重取一个新的。
                        GoogleTokenProvider.accessToken?.let { authSource.clearToken(it) }
                        if (allowSilentRetry && accountId != null && acquireTokenSilently(accountId)) {
                            fetchInto(date, accountId, allowSilentRetry = false)
                        } else {
                            GoogleTokenProvider.clear()
                            _uiState.update {
                                it.copy(
                                    connectionStatus = CalendarConnectionStatus.PERMISSION_REVOKED,
                                    errorMessage = "Google authorization expired, please reconnect",
                                )
                            }
                        }
                    }
                    is GoogleAuthRevokedException -> {
                        GoogleTokenProvider.clear()
                        _uiState.update {
                            it.copy(
                                connectionStatus = CalendarConnectionStatus.PERMISSION_REVOKED,
                                errorMessage = "Google authorization revoked, please reconnect",
                            )
                        }
                    }
                    else -> _uiState.update {
                        it.copy(
                            connectionStatus = CalendarConnectionStatus.SYNC_FAILED,
                            errorMessage = e.message ?: "Failed to load calendar",
                        )
                    }
                }
            }
    }

    /**
     * 为 [accountName] 静默取 token：成功写入 provider 返回 true；需同意 / 失败返回 false（不弹 UI）。
     * 静默路径下「需同意」视为授权失效，由调用方落到 PERMISSION_REVOKED。
     */
    private suspend fun acquireTokenSilently(accountName: String): Boolean =
        when (val outcome = authSource.fetchToken(accountName)) {
            is TokenOutcome.Success -> {
                GoogleTokenProvider.accessToken = outcome.token
                true
            }
            is TokenOutcome.NeedsConsent -> false
            is TokenOutcome.Failure -> {
                LogUtils.w("silent token failed", outcome.error, TAG)
                false
            }
        }

    /** 断开 Calendar：删 token + 删绑定 + 清缓存（不 revoke），回到未连接。 */
    private fun onDisconnect() {
        LogUtils.d("disconnect calendar", TAG)
        clearLocalSession()
        _uiState.update { CalendarUiState(selectedDate = it.selectedDate) }
    }

    /**
     * 重新授权前置清理：清 GMS token 缓存 + 服务端 revoke + 删 token/绑定/缓存。
     * Route 在此之后立即用当前登录账户重连（[connectWithCurrentAccount]）。
     */
    suspend fun prepareAccountSwitch() {
        LogUtils.d("prepare account switch (clearToken + revoke + clear)", TAG)
        GoogleTokenProvider.accessToken?.let { token ->
            authSource.clearToken(token)
            authSource.revoke(token)
        }
        clearLocalSession()
        _uiState.update { CalendarUiState(selectedDate = it.selectedDate) }
    }

    /** 连接失败的可读提示：区分网络错误与账号/授权配置问题。 */
    private fun connectFailureMessage(error: Throwable): String = when (error) {
        is java.io.IOException -> "Network error. Please retry."
        else -> "Couldn't connect this account to Google Calendar. " +
            "Make sure it's a Google account with Calendar access allowed."
    }

    /** 清本地会话（token + 绑定 + 缓存）。退出登录 / 断开 / 换账号共用。 */
    private fun clearLocalSession() {
        GoogleTokenProvider.clear()
        bindingStore.clear()
        eventCache.clear()
    }

    private companion object {
        const val TAG = "CalendarVM"
    }
}
