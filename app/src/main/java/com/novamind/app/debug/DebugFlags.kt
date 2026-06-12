package com.novamind.app.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Feature Flag 注册表（Debug 工具箱用）：内存态开关，便于模拟线上各种组合。
 * 业务代码可用 `DebugFlags.isOn("key")` 读取。仅 Debug 包生效。
 */
object DebugFlags {

    private val _flags = MutableStateFlow(
        linkedMapOf(
            "verbose_log" to false,        // 详细日志
            "show_block_borders" to false, // 显示编辑块边界
            "force_empty_state" to false,  // 强制空数据态
        )
    )
    val flags: StateFlow<Map<String, Boolean>> = _flags.asStateFlow()

    fun isOn(key: String): Boolean = _flags.value[key] == true

    fun toggle(key: String) {
        _flags.value = LinkedHashMap(_flags.value).apply { this[key] = !(this[key] ?: false) }
    }
}
