package com.novamind.app.feature.calendar

import com.novamind.app.data.calendar.CalendarEvent
import java.time.LocalDate

/**
 * Calendar 页面的单一不可变状态。会议/待办计数与时段分组都由 [events] 派生。
 */
data class CalendarUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    /** 是否已完成 Google 授权（决定显示空状态连接卡片还是事件列表）。 */
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    /** 选中日期的事件（已按开始时间排序）。 */
    val events: List<CalendarEvent> = emptyList(),
    val morningExpanded: Boolean = false,
    val afternoonExpanded: Boolean = false,
    /** 一次性错误提示文案，UI 消费后调用 [CalendarUiEvent.ErrorShown] 清除。 */
    val errorMessage: String? = null,
) {
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

    data class DateSelected(val date: LocalDate) : CalendarUiEvent
    data object PrevDay : CalendarUiEvent
    data object NextDay : CalendarUiEvent
    data object ToggleMorning : CalendarUiEvent
    data object ToggleAfternoon : CalendarUiEvent
    data object Refresh : CalendarUiEvent
    data object ErrorShown : CalendarUiEvent
}
