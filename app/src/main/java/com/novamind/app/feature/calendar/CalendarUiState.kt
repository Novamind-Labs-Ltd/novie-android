package com.novamind.app.feature.calendar

import com.novamind.app.common.google.GoogleAccount
import com.novamind.app.data.calendar.CalendarEvent
import java.time.LocalDate

/**
 * Calendar 页面的单一不可变状态。
 *
 * [connectionStatus] 是唯一渲染依据（见 [CalendarConnectionStatus] 状态机）；
 * [isConnected] / [isLoading] 由它派生，仅为兼容现有 UI。
 * 会议/待办计数与时段分组都由 [events] 派生。
 */
data class CalendarUiState(
    val connectionStatus: CalendarConnectionStatus = CalendarConnectionStatus.NOT_CONNECTED,
    /** 当前绑定的 Google 账号（用于展示与「换账号」入口）。 */
    val account: GoogleAccount? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    /** 选中日期的事件（已按开始时间排序）。 */
    val events: List<CalendarEvent> = emptyList(),
    val morningExpanded: Boolean = false,
    val afternoonExpanded: Boolean = false,
    /** 一次性错误提示文案，UI 消费后调用 [CalendarUiEvent.ErrorShown] 清除。 */
    val errorMessage: String? = null,
) {
    /** 已连接：非「未连接 / 授权被撤销」态都视为已连接（含同步中、过期续期中、同步失败）。 */
    val isConnected: Boolean
        get() = connectionStatus != CalendarConnectionStatus.NOT_CONNECTED &&
            connectionStatus != CalendarConnectionStatus.PERMISSION_REVOKED

    /** 正在同步（拉取事件）。 */
    val isLoading: Boolean get() = connectionStatus == CalendarConnectionStatus.SYNCING

    /** 需要用户重新授权（撤销后）。 */
    val needsReconnect: Boolean get() = connectionStatus == CalendarConnectionStatus.PERMISSION_REVOKED

    /** 同步失败，可重试。 */
    val syncFailed: Boolean get() = connectionStatus == CalendarConnectionStatus.SYNC_FAILED

    val meetingCount: Int get() = events.count { it.isMeeting }
    val todoCount: Int get() = events.count { !it.isMeeting }
    val morningEvents: List<CalendarEvent> get() = events.filter { it.isMorning }
    val afternoonEvents: List<CalendarEvent> get() = events.filter { !it.isMorning }
}

/** UI → ViewModel 的单一事件入口类型。 */
sealed interface CalendarUiEvent {
    /** 点击「连接 Google 日历」。实际授权流程在 Route 层（需 Activity）发起。 */
    data object Connect : CalendarUiEvent

    /** Route 层授权成功后回传 access token。 */
    data class GoogleTokenObtained(val accessToken: String) : CalendarUiEvent

    /** 授权流程失败 / 被取消。 */
    data class AuthFailed(val message: String?) : CalendarUiEvent

    /** 断开 Calendar：删除 token + 删除绑定 + 清缓存（不 revoke）。 */
    data object Disconnect : CalendarUiEvent

    /** 换 Google 账号：revoke 旧授权 + 清旧缓存 + 重新走同意。Route 层编排。 */
    data object SwitchAccount : CalendarUiEvent

    /** 同步失败后重试。 */
    data object Retry : CalendarUiEvent

    data class DateSelected(val date: LocalDate) : CalendarUiEvent
    data object PrevDay : CalendarUiEvent
    data object NextDay : CalendarUiEvent
    data object ToggleMorning : CalendarUiEvent
    data object ToggleAfternoon : CalendarUiEvent
    data object Refresh : CalendarUiEvent
    data object ErrorShown : CalendarUiEvent
}
