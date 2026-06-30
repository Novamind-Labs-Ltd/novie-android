package com.novamind.app.feature.calendar

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/**
 * Calendar 的有状态路由：连接 [CalendarViewModel] 与无状态的 [CalendarScreen]。
 *
 * 日历账户跟随 App 登录账户，**不弹账号选择器**：
 * - [CalendarUiEvent.Connect] / [CalendarUiEvent.SwitchAccount] → 直接用当前登录账户取 token；
 * - VM 取 token 若需用户同意（首次授权 calendar 范围），通过 [CalendarViewModel.consentRequest]
 *   请求启动 OAuth 同意页，返回后调 [CalendarViewModel.onConsentGranted] 重试。
 */
@Composable
fun CalendarRoute(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // 恢复授权（OAuth 同意）结果：同意后用登录账户重试取 token。
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onConsentGranted()
        } else {
            viewModel.onEvent(CalendarUiEvent.AuthFailed("Authorization cancelled"))
        }
    }

    // VM 请求同意时启动恢复意图。
    LaunchedEffect(Unit) {
        viewModel.consentRequest.collect { intent -> consentLauncher.launch(intent) }
    }

    // 每次页面显示（进入/切回 tab/回前台）都刷新。
    LifecycleResumeEffect(Unit) {
        viewModel.onScreenShown()
        onPauseOrDispose { }
    }

    CalendarScreen(
        uiState = uiState,
        onEvent = { event ->
            when (event) {
                // 连接：直接用当前登录账户取 token。
                CalendarUiEvent.Connect -> viewModel.connectWithCurrentAccount()
                // 重新授权当前账户：先清/吊销旧授权，再用登录账户重连。
                CalendarUiEvent.SwitchAccount -> scope.launch {
                    viewModel.prepareAccountSwitch()
                    viewModel.connectWithCurrentAccount()
                }
                else -> viewModel.onEvent(event)
            }
        },
        modifier = modifier,
    )
}
