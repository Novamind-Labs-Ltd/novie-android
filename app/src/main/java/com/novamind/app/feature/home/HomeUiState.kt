package com.novamind.app.feature.home

import com.novamind.app.data.tasks.CalendarTask
import com.novamind.app.data.calendar.CalendarEvent
import com.novamind.app.feature.create.model.NoteItem
import java.time.LocalDate

data class UpcomingItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconResId: Int,
    /** 计划时间（如「10:00」）；home_final 卡片左侧展示，为空则不显示。 */
    val time: String = "",
    /** 是否为会议：true=会议卡（带「Start notes」按钮），false=任务卡（不带）。 */
    val isMeeting: Boolean = false,
    /** Google Calendar 事件开始日期；示例数据可为空。 */
    val date: LocalDate? = null,
    /** 是否为全天事件。 */
    val isAllDay: Boolean = false,
)

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val upcomingItems: List<UpcomingItem> = emptyList(),
    /** 首页今日 Up next 卡片对应的原始日历事件，用于点击卡片打开会议详情。 */
    val upcomingEvents: List<CalendarEvent> = emptyList(),
    /** Upcoming 页面未来两周的会议；与首页今日 Up next 分开，避免首页混入未来日期。 */
    val upcomingRangeItems: List<UpcomingItem> = emptyList(),
    /** Upcoming 卡片对应的原始日历事件，用于点击卡片打开会议详情。 */
    val upcomingRangeEvents: List<CalendarEvent> = emptyList(),
    val upcomingRangeLoading: Boolean = false,
    /** 今日 Google Tasks 原始数据（首页 Up next 不展示任务卡，保留供后续功能使用）。 */
    val todayTasks: List<CalendarTask> = emptyList(),
    val notes: List<NoteItem> = emptyList(),
    /** Up next 未授权 Google 日历：展示「连接日历」入口（有可连接账号但未授权时）。 */
    val calendarNeedsAuth: Boolean = false,
    /** 正在走日历连接 / 授权同意流程（按钮显示 loading，避免重复点击）。 */
    val calendarConnecting: Boolean = false,
    val errorMessage: String? = null,
)
