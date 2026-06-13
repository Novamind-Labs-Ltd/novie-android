package com.novamind.app.common.web.bridge

import org.json.JSONArray

/**
 * 注入到 H5 页面的 JS SDK（`window.NovieBridge`）。
 * 在 onPageStarted 时 evaluateJavascript 注入，确保首屏可用。
 *
 * 提供：
 * - `call(api, params, timeout?) -> Promise`：Promise 化的异步调用，带超时兜底（默认 8s）。
 * - `on/off(event, fn)`：事件订阅。
 * - `hasApi(api) -> boolean`：能力查询（基于 Native 下发的 API 清单）。
 * - 注入完成后派发 `NovieBridgeReady` 事件，并 flush ready 队列。
 */
object BridgeJsSdk {

    fun script(apiNames: List<String>): String {
        val apisJson = JSONArray(apiNames).toString()
        return """
(function () {
  if (window.NovieBridge && window.NovieBridge.__installed) return;
  var callbacks = {};
  var seq = 0;
  var APIS = $apisJson;

  var NovieBridge = {
    __installed: true,
    call: function (api, params, timeout) {
      timeout = timeout || 8000;
      return new Promise(function (resolve, reject) {
        var parts = api.split(".");
        if (parts.length !== 2) { reject({ code: 1001, message: "bad api name: " + api }); return; }
        var callId = "c_" + Date.now() + "_" + (++seq);
        var timer = setTimeout(function () {
          delete callbacks[callId];
          reject({ code: 1005, message: "timeout: " + api });
        }, timeout);
        callbacks[callId] = { resolve: resolve, reject: reject, timer: timer };
        try {
          window.__novieBridge__.postMessage(JSON.stringify({
            callId: callId, namespace: parts[0], api: parts[1],
            params: params || {}, version: "1.0"
          }));
        } catch (e) {
          clearTimeout(timer); delete callbacks[callId];
          reject({ code: 1501, message: "bridge unavailable" });
        }
      });
    },
    _onResponse: function (res) {
      var cb = callbacks[res.callId];
      if (!cb) return;
      clearTimeout(cb.timer); delete callbacks[res.callId];
      if (res.code === 0) cb.resolve(res.data); else cb.reject(res);
    },
    _events: {},
    on: function (evt, fn) { (this._events[evt] = this._events[evt] || []).push(fn); },
    off: function (evt, fn) {
      var a = this._events[evt] || [];
      this._events[evt] = a.filter(function (f) { return f !== fn; });
    },
    _onEvent: function (e) {
      (this._events[e.event] || []).forEach(function (fn) {
        try { fn(e.data); } catch (err) {}
      });
    },
    hasApi: function (api) { return APIS.indexOf(api) >= 0; },
    apis: function () { return APIS.slice(); }
  };

  window.NovieBridge = NovieBridge;
  try {
    window.dispatchEvent(new Event("NovieBridgeReady"));
  } catch (e) {}
})();
""".trimIndent()
    }
}
