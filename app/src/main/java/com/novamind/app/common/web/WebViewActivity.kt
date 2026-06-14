package com.novamind.app.common.web

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.novamind.app.common.web.bridge.SourceLevel
import com.novamind.app.ui.theme.AppTheme

/**
 * 独立进程承载 WebView（清单声明 android:process=":web"）。
 * 网页/容器即使崩溃也只影响 ":web" 进程，不会带崩主进程。
 *
 * JSBridge 的来源等级默认由 [com.novamind.app.common.web.bridge.DomainWhitelist] 按 URL
 * 动态判定；[start] 的 debugLevel 仅供调试强制覆盖（静态字段不跨进程，故经 Intent 透传）。
 */
class WebViewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val override = intent.getStringExtra(EXTRA_DEBUG_LEVEL)
            ?.let { name -> runCatching { SourceLevel.valueOf(name) }.getOrNull() }
        setContent {
            AppTheme {
                WebViewScreen(
                    url = url,
                    onBack = { finish() },
                    modifier = Modifier.fillMaxSize(),
                    levelOverride = override,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_DEBUG_LEVEL = "extra_debug_level"

        /**
         * @param debugLevel 仅调试用：强制 JSBridge 来源等级；为 null 则按域名白名单判定。
         */
        fun start(context: Context, url: String, debugLevel: SourceLevel? = null) {
            context.startActivity(
                Intent(context, WebViewActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_DEBUG_LEVEL, debugLevel?.name)
            )
        }
    }
}
