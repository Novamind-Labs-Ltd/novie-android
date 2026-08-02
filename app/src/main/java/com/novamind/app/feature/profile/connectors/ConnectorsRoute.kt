package com.novamind.app.feature.profile.connectors

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ConnectorsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectorsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    ConnectorsScreen(
        uiState = uiState,
        onBack = onBack,
        onRevokeGoogleCalendar = viewModel::revokeGoogleCalendar,
        onDismissMessage = viewModel::clearMessage,
        modifier = modifier,
    )
}
