package com.novamind.app.feature.calendar

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * 需 Activity 的交互式步骤放在这里：
 * - [CalendarUiEvent.Connect] / [CalendarUiEvent.SwitchAccount] → 弹账号选择器，
 *   用户选定后调 [CalendarViewModel.onGoogleAccountChosen] 取 token；
 * - VM 取 token 若需用户同意，通过 [CalendarViewModel.consentRequest] 请求启动恢复意图，
 *   返回后调 [CalendarViewModel.onConsentGranted] 重试。
 */
@Composable
fun CalendarRoute(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authSource = remember { GoogleCalendarAuthManager(context) }

    // 账号选择器结果：取出所选 Google 账号名（邮箱），交给 VM 取 token。
    val accountChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val name = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!name.isNullOrBlank()) {
                viewModel.onGoogleAccountChosen(name)
            } else {
                viewModel.onEvent(CalendarUiEvent.AuthFailed("No account selected"))
            }
        } else {
            viewModel.onEvent(CalendarUiEvent.AuthFailed("Account selection cancelled"))
        }
    }

    // 恢复授权（同意）结果：同意后让 VM 用已选账号重试取 token。
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

    fun chooseAccount() {
        accountChooserLauncher.launch(authSource.newAccountChooserIntent())
    }

    CalendarScreen(
        uiState = uiState,
        onEvent = { event ->
            when (event) {
                // 首次连接：弹账号选择器。
                CalendarUiEvent.Connect -> chooseAccount()
                // 换账号：先清/吊销旧授权，再弹选择器选新账号。
                CalendarUiEvent.SwitchAccount -> scope.launch {
                    viewModel.prepareAccountSwitch()
                    chooseAccount()
                }
                else -> viewModel.onEvent(event)
            }
        },
        modifier = modifier,
    )
}
