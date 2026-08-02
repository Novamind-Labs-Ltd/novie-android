package com.novamind.app.feature.profile.connectors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.common.google.GoogleCalendarAuthSource
import com.novamind.app.common.google.GoogleTokenProvider
import com.novamind.app.common.google.TokenOutcome
import com.novamind.app.data.calendar.CalendarEventCache
import com.novamind.app.feature.calendar.CalendarBindingStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ConnectorsViewModel @Inject constructor(
    private val authSource: GoogleCalendarAuthSource,
    private val bindingStore: CalendarBindingStore,
    private val eventCache: CalendarEventCache,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectorsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update {
            it.copy(
                googleCalendarConnected = bindingStore.isConnected,
                googleAccountEmail = bindingStore.accountEmail,
            )
        }
    }

    fun revokeGoogleCalendar() {
        if (_uiState.value.revoking || !_uiState.value.googleCalendarConnected) return

        viewModelScope.launch {
            _uiState.update { it.copy(revoking = true, message = null) }

            val email = bindingStore.accountEmail
            var token = GoogleTokenProvider.accessToken
            if (token == null && email != null) {
                token = (authSource.fetchToken(email) as? TokenOutcome.Success)?.token
            }

            val serverRevoked = token != null
            token?.let {
                authSource.clearToken(it)
                authSource.revoke(it)
            }

            GoogleTokenProvider.clear()
            bindingStore.clear()
            eventCache.clear()

            _uiState.value = ConnectorsUiState(
                message = if (serverRevoked) {
                    "Google Calendar authorization revoked. Reconnecting will require consent again."
                } else {
                    "Local Google Calendar connection cleared. You may also revoke access in your Google Account."
                },
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
