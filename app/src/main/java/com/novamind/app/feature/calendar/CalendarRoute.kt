package com.novamind.app.feature.calendar

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.common.google.GoogleCalendarAuthManager
import kotlinx.coroutines.launch

/**
 * Calendar 的有状态路由：连接 [CalendarViewModel] 与无状态的 [CalendarScreen]。
 *
 * Google 授权需要 Activity（同意流程通过 [android.content.IntentSender] 启动），
 * 因此把授权编排放在这里——拦截 [CalendarUiEvent.Connect]，调用 [GoogleCalendarAuthManager]，
 * 成功后把 access token 通过 [CalendarUiEvent.GoogleTokenObtained] 回传给 ViewModel。
 */
@Composable
fun CalendarRoute(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authManager = remember { GoogleCalendarAuthManager(context) }

    // 用户同意/选账号流程的结果：
    // - RESULT_OK 但解析抛错（如 OAuth client / SHA-1 / Calendar API 未配好）→ 暴露真实错误信息；
    // - RESULT_OK 且拿到 token → 回传；
    // - 其余（用户真正取消）→ 上报取消。
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            runCatching { authManager.tokenFromConsentResult(result.data) }
                .onSuccess { token ->
                    if (!token.isNullOrBlank()) {
                        viewModel.onEvent(CalendarUiEvent.GoogleTokenObtained(token))
                    } else {
                        viewModel.onEvent(CalendarUiEvent.AuthFailed("No access token returned"))
                    }
                }
                .onFailure { e ->
                    viewModel.onEvent(CalendarUiEvent.AuthFailed("Authorization failed: ${e.message}"))
                }
        } else {
            viewModel.onEvent(CalendarUiEvent.AuthFailed("Authorization cancelled"))
        }
    }

    fun startAuthorization() {
        scope.launch {
            runCatching { authManager.requestAuthorization() }
                .onSuccess { outcome ->
                    when (outcome) {
                        is GoogleCalendarAuthManager.Outcome.Authorized ->
                            viewModel.onEvent(CalendarUiEvent.GoogleTokenObtained(outcome.accessToken))
                        is GoogleCalendarAuthManager.Outcome.NeedsConsent ->
                            consentLauncher.launch(
                                IntentSenderRequest.Builder(outcome.intentSender).build(),
                            )
                    }
                }
                .onFailure { e ->
                    viewModel.onEvent(CalendarUiEvent.AuthFailed(e.message))
                }
        }
    }

    CalendarScreen(
        uiState = uiState,
        onEvent = { event ->
            if (event is CalendarUiEvent.Connect) startAuthorization() else viewModel.onEvent(event)
        },
        modifier = modifier,
    )
}
