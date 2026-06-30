package com.novamind.app.feature.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.NovieApplication
import com.novamind.app.common.google.GoogleTokenProvider
import com.novamind.app.data.calendar.GoogleAuthExpiredException
import com.novamind.app.data.calendar.GoogleCalendarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Calendar 页面 ViewModel：持有授权状态与选中日期的 Google 日历事件。
 *
 * 授权交互（选账号/同意）需要 Activity，放在 [CalendarRoute]；本类只负责：
 * 收到 token 后写入 [GoogleTokenProvider]、按日期拉取事件、维护 UI 状态。
 */
class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: GoogleCalendarRepository =
        (application as NovieApplication).googleCalendarRepository

    private val _uiState = MutableStateFlow(
        CalendarUiState(isConnected = GoogleTokenProvider.isAuthorized),
    )
    val uiState = _uiState.asStateFlow()

    init {
        // 进程内若已授权（同一会话内返回该页），直接加载当天事件。
        if (GoogleTokenProvider.isAuthorized) loadEvents()
    }

    fun onEvent(event: CalendarUiEvent) {
        when (event) {
            // Connect 由 Route 拦截发起授权流程，VM 不处理。
            CalendarUiEvent.Connect -> Unit
            is CalendarUiEvent.GoogleTokenObtained -> onTokenObtained(event.accessToken)
            is CalendarUiEvent.AuthFailed -> _uiState.update {
                it.copy(errorMessage = event.message ?: "Google authorization failed")
            }
            is CalendarUiEvent.DateSelected -> selectDate(event.date)
            CalendarUiEvent.PrevDay -> selectDate(_uiState.value.selectedDate.minusDays(1))
            CalendarUiEvent.NextDay -> selectDate(_uiState.value.selectedDate.plusDays(1))
            CalendarUiEvent.ToggleMorning -> _uiState.update { it.copy(morningExpanded = !it.morningExpanded) }
            CalendarUiEvent.ToggleAfternoon -> _uiState.update { it.copy(afternoonExpanded = !it.afternoonExpanded) }
            CalendarUiEvent.Refresh -> loadEvents()
            CalendarUiEvent.ErrorShown -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun onTokenObtained(token: String) {
        GoogleTokenProvider.accessToken = token
        _uiState.update { it.copy(isConnected = true) }
        loadEvents()
    }

    private fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        if (_uiState.value.isConnected) loadEvents()
    }

    private fun loadEvents() {
        val date = _uiState.value.selectedDate
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.eventsOn(date) }
                .onSuccess { events ->
                    _uiState.update { it.copy(isLoading = false, events = events) }
                }
                .onFailure { e ->
                    val expired = e is GoogleAuthExpiredException
                    if (expired) GoogleTokenProvider.clear()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            events = if (expired) emptyList() else it.events,
                            isConnected = if (expired) false else it.isConnected,
                            errorMessage = if (expired) {
                                "Google authorization expired, please reconnect"
                            } else {
                                e.message ?: "Failed to load calendar"
                            },
                        )
                    }
                }
        }
    }
}
