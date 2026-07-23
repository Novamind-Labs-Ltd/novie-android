package com.novamind.app.data.calendar

import com.novamind.app.common.google.GoogleCalendarAuthSource
import com.novamind.app.common.google.GoogleTokenProvider
import com.novamind.app.common.google.TokenOutcome
import com.novamind.app.common.session.UserSessionManager
import com.novamind.app.data.tasks.CalendarTask
import com.novamind.app.data.tasks.GoogleTasksRepository
import com.novamind.app.feature.calendar.CalendarBindingStore
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** 今日议程：会议(events) + 任务(tasks)，附带授权状态。 */
data class TodayAgenda(
    val events: List<CalendarEvent> = emptyList(),
    val tasks: List<CalendarTask> = emptyList(),
    /** 是否已静默授权成功（拿到可用 token，议程数据来自日历）。 */
    val authorized: Boolean = false,
    /** 是否有可用于连接的账号（有登录邮箱）——决定是否展示「连接日历」入口。 */
    val accountAvailable: Boolean = false,
)

/**
 * 共享用例：静默确保 Google 授权后，拉取「今天」的会议 + 任务。
 *
 * 抽出日历页的「静默授权 + 取数」核心逻辑，供首页 Up next 等复用，避免各处重复实现，
 * 也让首页获得与日历页一致的静默续期能力（token 过期自动重取一次）。
 *
 * 只读、尽力而为：未连接 / 未授权 / 网络错误一律返回空议程，不抛异常，
 * 也**不修改**连接绑定状态（建立/切换绑定仍由 Calendar 页负责）。
 */
@Singleton
class TodayAgendaUseCase @Inject constructor(
    private val calendarRepository: GoogleCalendarRepository,
    private val tasksRepository: GoogleTasksRepository,
    private val authSource: GoogleCalendarAuthSource,
    private val bindingStore: CalendarBindingStore,
) {
    suspend operator fun invoke(): TodayAgenda {
        // 账号：优先已绑定账号，否则探测当前登录账户（只读，不建立绑定）
        val account = (bindingStore.accountEmail?.takeIf { bindingStore.isConnected }
            ?: UserSessionManager.current.userKey)?.takeIf { it.isNotBlank() }
            ?: return TodayAgenda()

        // 有账号但静默授权失败 → 未授权（accountAvailable=true 供 UI 展示「连接」按钮）
        if (!acquireTokenSilently(account)) return TodayAgenda(accountAvailable = true)

        val today = LocalDate.now()
        return TodayAgenda(
            events = fetchEvents(today, account),
            tasks = safe { tasksRepository.tasksOn(today) },
            authorized = true,
            accountAvailable = true,
        )
    }

    /** 拉会议：401 过期时清 token → 静默重取 → 再试一次；仍失败返回空。 */
    private suspend fun fetchEvents(date: LocalDate, account: String): List<CalendarEvent> =
        try {
            calendarRepository.eventsOn(date)
        } catch (c: CancellationException) {
            throw c
        } catch (e: GoogleAuthExpiredException) {
            GoogleTokenProvider.accessToken?.let { authSource.clearToken(it) }
            if (acquireTokenSilently(account)) safe { calendarRepository.eventsOn(date) } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

    private suspend fun acquireTokenSilently(account: String): Boolean =
        when (val outcome = authSource.fetchToken(account)) {
            is TokenOutcome.Success -> {
                GoogleTokenProvider.accessToken = outcome.token
                true
            }
            else -> false
        }

    private suspend fun <T> safe(block: suspend () -> List<T>): List<T> =
        try {
            block()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            emptyList()
        }
}
