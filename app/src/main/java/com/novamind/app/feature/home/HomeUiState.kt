package com.novamind.app.feature.home

import com.novamind.app.data.tasks.CalendarTask
import com.novamind.app.feature.create.model.NoteItem

data class UpcomingItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconResId: Int,
    /** 计划时间（如「10:00」）；home_final 卡片左侧展示，为空则不显示。 */
    val time: String = "",
    /** 是否为会议：true=会议卡（带「Start notes」按钮），false=任务卡（不带）。 */
    val isMeeting: Boolean = false,
)

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val upcomingItems: List<UpcomingItem> = emptyList(),
    /** Up next 中「任务」对应的原始 Google Tasks（用于点击进入详情/编辑）。 */
    val todayTasks: List<CalendarTask> = emptyList(),
    val notes: List<NoteItem> = emptyList(),
    /** Up next 未授权 Google 日历：展示「连接日历」入口（有可连接账号但未授权时）。 */
    val calendarNeedsAuth: Boolean = false,
    /** 正在走日历连接 / 授权同意流程（按钮显示 loading，避免重复点击）。 */
    val calendarConnecting: Boolean = false,
    val errorMessage: String? = null,
)
