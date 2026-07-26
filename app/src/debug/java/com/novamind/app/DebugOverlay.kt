package com.novamind.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.novamind.app.common.config.AppConfig
import com.novamind.app.debug.DebugPanel
import com.novamind.app.debug.ShakeDetector

/**
 * Debug 变体的调试入口：摇一摇打开 Debug 工具箱。
 * release 变体提供同名空实现（见 src/release），因此调试工具与其依赖都不进 release 包。
 *
 * @param onNavigate 工具箱内点击页面跳转时回调（route）。
 */
@Composable
fun DebugOverlay(onNavigate: (String) -> Unit) {
    var showDebug by rememberSaveable { mutableStateOf(false) }
    val allowShakeToOpen = AppConfig.Debug.ALLOW_SHAKE_TO_OPEN_PANEL
    ShakeDetector(enabled = allowShakeToOpen) { showDebug = true }
    if (allowShakeToOpen && showDebug) {
        DebugPanel(
            onDismiss = { showDebug = false },
            onNavigate = { route ->
                showDebug = false
                onNavigate(route)
            },
        )
    }
}
