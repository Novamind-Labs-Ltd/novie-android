package com.novamind.app.feature.profile.connectors

data class ConnectorsUiState(
    val googleCalendarConnected: Boolean = false,
    val googleAccountEmail: String? = null,
    val revoking: Boolean = false,
    val message: String? = null,
)
