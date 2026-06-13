package com.novamind.app.common.web.bridge

import android.webkit.JavascriptInterface

/**
 * 暴露给 JS 的唯一入口对象（`window.__novieBridge__`）。
 * 只提供 [postMessage] 一个方法，绝不暴露业务对象/反射可达对象
 * （规避历史 addJavascriptInterface 任意代码执行漏洞）。
 */
class NovieBridgeInterface(private val dispatcher: BridgeDispatcher) {
    @JavascriptInterface
    fun postMessage(raw: String) {
        dispatcher.onMessage(raw)
    }
}
