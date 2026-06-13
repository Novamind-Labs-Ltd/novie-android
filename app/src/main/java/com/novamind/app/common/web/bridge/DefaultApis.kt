package com.novamind.app.common.web.bridge

import com.novamind.app.common.web.bridge.handlers.CloseHandler
import com.novamind.app.common.web.bridge.handlers.DeviceInfoHandler
import com.novamind.app.common.web.bridge.handlers.ToastHandler

/** P0 默认 API 集合。后续新增 Handler 在此登记。 */
object DefaultApis {
    fun registry(): ApiRegistry = ApiRegistry().registerAll(
        listOf(
            ToastHandler(),
            DeviceInfoHandler(),
            CloseHandler(),
        )
    )
}
