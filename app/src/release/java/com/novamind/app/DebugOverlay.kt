package com.novamind.app

import androidx.compose.runtime.Composable

/**
 * release 变体的调试入口空实现：不引用任何调试工具，保证调试代码不进 release 包。
 */
@Composable
fun DebugOverlay(onNavigate: (String) -> Unit) {
    // no-op in release
}
