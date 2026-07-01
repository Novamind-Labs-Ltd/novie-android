package com.novamind.app.feature.calendar

import com.novamind.app.common.google.GoogleAccount
import com.novamind.app.data.calendar.CalendarEvent
import com.novamind.app.data.tasks.CalendarTask
import java.time.LocalDate

/**
 * Calendar 页面的单一不可变状态。
 *
 * [connectionStatus] 是唯一渲染依据（见 [CalendarConnectionStatus] 状态机）；
 * [isConnected] / [isLoading] 由它派生，仅为兼容现有 UI。
 * 「活动」([events], Calendar) 与「任务」([tasks], Google Tasks) 合并为按时间排序的 [agenda]。
 */
data class CalendarUiState(
    val connectionStatus: CalendarConnectionStatus = CalendarConnectionStatus.NOT_CONNECTED,
    /** 当前绑定的 Google 账号（用于展示与「换账号」入口）。 */
    val account: GoogleAccount? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    /** 选中日期的活动（Calendar events，已按开始时间排序）。 */
    val events: List<CalendarEvent> = emptyList(),
    /** 选中日期的任务（Google Tasks）。 */
    val tasks: List<CalendarTask> = emptyList(),
    /** 议程展示过滤：全部 / 仅活动 / 仅任务（点击统计卡切换）。 */
    val filter: AgendaFilter = AgendaFilter.ALL,
    /** 一次性错误提示文案，UI 消费后调用 [CalendarUiEvent.ErrorShown] 清除。 */
    val errorMessage: String? = null,
) {
    /** 已连接：仅「同步中/已连接/过期续期中/同步失败」视为已连接，可展示事件区域。 */
    val isConnected: Boolean
        get() = connectionStatus == CalendarConnectionStatus.SYNCING ||
            connectionStatus == CalendarConnectionStatus.CONNECTED ||
            connectionStatus == CalendarConnectionStatus.TOKEN_EXPIRED ||
            connectionStatus == CalendarConnectionStatus.SYNC_FAILED

    /** 正在同步（拉取事件）。 */
    val isLoading: Boolean get() = connectionStatus == CalendarConnectionStatus.SYNCING

    /** 游客会话，不可用日历——显示「登录后使用」拦截态。 */
    val loginRequired: Boolean get() = connectionStatus == CalendarConnectionStatus.LOGIN_REQUIRED

    /** 需要用户重新授权（撤销后）。 */
    val needsReconnect: Boolean get() = connectionStatus == CalendarConnectionStatus.PERMISSION_REVOKED

    /** 同步失败，可重试。 */
    val syncFailed: Boolean get() = connectionStatus == CalendarConnectionStatus.SYNC_FAILED

    val eventCount: Int get() = events.size
    val taskCount: Int get() = tasks.size

    /** 是否展示活动区块（全部 / 仅活动）。 */
    val showEventsSection: Boolean get() = filter == AgendaFilter.ALL || filter == AgendaFilter.EVENTS

    /** 是否展示任务区块（全部 / 仅任务）。 */
    val showTasksSection: Boolean get() = filter == AgendaFilter.ALL || filter == AgendaFilter.TASKS
}

/** 议程展示过滤维度。 */
enum class AgendaFilter { ALL, EVENTS, TASKS }

/** UI → ViewModel 的单一事件入口类型。 */
sealed interface CalendarUiEvent {
    /** 点击「连接 Google 日历」。Route 层弹账号选择器，选定后调 VM 取 token。 */
    data object Connect : CalendarUiEvent

    /** 授权流程失败 / 被取消。 */
    data class AuthFailed(val message: String?) : CalendarUiEvent

    /** 断开 Calendar：删除 token + 删除绑定 + 清缓存（不 revoke）。 */
    data object Disconnect : CalendarUiEvent

    /** 换 Google 账号：revoke 旧授权 + 清旧缓存 + 重新走同意。Route 层编排。 */
    data object SwitchAccount : CalendarUiEvent

    /** 同步失败后重试。 */
    data object Retry : CalendarUiEvent

    /** 点击统计卡切换议程过滤（再次点击已选项回到全部）。 */
    data class SelectAgendaFilter(val filter: AgendaFilter) : CalendarUiEvent

    /** 右滑任务将其标记为已完成（乐观更新，失败回滚）。 */
    data class CompleteTask(val task: CalendarTask) : CalendarUiEvent

    /** 点击顶部「+」打开新增任务页。Route 层拦截显示覆盖层，VM 不处理。 */
    data object AddTaskClicked : CalendarUiEvent

    /** 新增任务页点 Save：在默认列表创建任务（notes 可空；due 为截止日期）。 */
    data class CreateTask(val title: String, val notes: String?, val due: LocalDate) : CalendarUiEvent

    /** 点击任务行进入编辑页。Route 层拦截显示覆盖层（编辑模式），VM 不处理。 */
    data class TaskClicked(val task: CalendarTask) : CalendarUiEvent

    /** 编辑任务页点 Save：更新标题/描述/截止日期。 */
    data class UpdateTask(
        val task: CalendarTask,
        val title: String,
        val notes: String?,
        val due: LocalDate,
    ) : CalendarUiEvent

    data class DateSelected(val date: LocalDate) : CalendarUiEvent
    data object PrevDay : CalendarUiEvent
    data object NextDay : CalendarUiEvent
    data object Refresh : CalendarUiEvent
    data object ErrorShown : CalendarUiEvent
}
