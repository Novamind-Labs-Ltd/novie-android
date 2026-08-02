package com.novamind.app.feature.profile.connectors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.components.TopBarBackButton
import com.novamind.app.ui.theme.AppTheme

private val PageBackground: Color @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
private val CardBackground: Color @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val PrimaryText: Color @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val SecondaryText: Color @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
private val SuccessText: Color @Composable @ReadOnlyComposable get() = TextColors.Success.default.current()
private val SuccessBackground: Color @Composable @ReadOnlyComposable get() = BackgroundColors.Success.tertiary.current()
private val DangerBackground: Color @Composable @ReadOnlyComposable get() = ButtonColors.Destructive.background.current()
private val DangerText: Color @Composable @ReadOnlyComposable get() = ButtonColors.Destructive.text.current()

@Composable
fun ConnectorsScreen(
    uiState: ConnectorsUiState,
    onBack: () -> Unit,
    onRevokeGoogleCalendar: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    var showRevokeConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 22.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TopBarBackButton(onClick = onBack)
            Text(
                text = "Connectors",
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                color = PrimaryText,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        Text(
            text = "Connected apps",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryText,
            modifier = Modifier.padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 12.dp),
        )

        GoogleCalendarCard(
            uiState = uiState,
            onRevoke = { showRevokeConfirmation = true },
        )
    }

    if (showRevokeConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!uiState.revoking) showRevokeConfirmation = false },
            title = { Text("Revoke Google Calendar?") },
            text = {
                Text(
                    "This removes My Novie's access to Google Calendar and Tasks. You'll need to grant consent again to reconnect."
                )
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.revoking,
                    onClick = { showRevokeConfirmation = false },
                ) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.revoking,
                    onClick = {
                        showRevokeConfirmation = false
                        onRevokeGoogleCalendar()
                    },
                ) { Text("Revoke", color = TextColors.Error.default.current()) }
            },
        )
    }

    uiState.message?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissMessage,
            title = { Text("Google Calendar") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDismissMessage) { Text("Close") } },
        )
    }
}

@Composable
private fun GoogleCalendarCard(uiState: ConnectorsUiState, onRevoke: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CardBackground,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(BackgroundColors.Interactive.secondary.current()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_nav_calendar),
                        contentDescription = null,
                        tint = IconColors.Default.default.current(),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Google Calendar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                    Text(
                        uiState.googleAccountEmail ?: "Not connected",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = SecondaryText,
                    )
                }
                if (uiState.googleCalendarConnected) {
                    Text(
                        "Connected",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = SuccessText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SuccessBackground)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            if (uiState.googleCalendarConnected) {
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(DangerBackground)
                        .clickable(
                            enabled = !uiState.revoking,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            onClick = onRevoke,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (uiState.revoking) {
                        CircularProgressIndicator(
                            color = DangerText,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text("Revoke authorization", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DangerText)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ConnectorsScreenPreview() {
    AppTheme {
        ConnectorsScreen(
            uiState = ConnectorsUiState(
                googleCalendarConnected = true,
                googleAccountEmail = "jam@example.com",
            ),
            onBack = {},
            onRevokeGoogleCalendar = {},
            onDismissMessage = {},
        )
    }
}
