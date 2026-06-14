package com.novamind.app.common.web.bridge

import com.novamind.app.common.web.bridge.handlers.CloseHandler
import com.novamind.app.common.web.bridge.handlers.DeviceInfoHandler
import com.novamind.app.common.web.bridge.handlers.ToastHandler
import com.novamind.app.common.web.bridge.handlers.TokenHandler
import com.novamind.app.common.web.bridge.handlers.UserInfoHandler

/** 默认 API 集合。后续新增 Handler 在此登记。 */
object DefaultApis {
    fun registry(): ApiRegistry = ApiRegistry().registerAll(
        listOf(
            // PUBLIC
            ToastHandler(),
            DeviceInfoHandler(),
            CloseHandler(),
            // AUTHED
            TokenHandler(),
            // PRIVATE
            UserInfoHandler(),
        )
    )
}
