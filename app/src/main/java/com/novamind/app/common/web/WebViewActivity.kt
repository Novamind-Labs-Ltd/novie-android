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
 */
class WebViewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val level = runCatching {
            SourceLevel.valueOf(intent.getStringExtra(EXTRA_SOURCE_LEVEL) ?: SourceLevel.UNKNOWN.name)
        }.getOrDefault(SourceLevel.UNKNOWN)
        setContent {
            AppTheme {
                WebViewScreen(
                    url = url,
                    onBack = { finish() },
                    modifier = Modifier.fillMaxSize(),
                    sourceLevel = level,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_SOURCE_LEVEL = "extra_source_level"

        /**
         * @param trusted 标记为一方可信来源（可调用全部 JSAPI）。外链默认 false（仅 PUBLIC）。
         */
        fun start(context: Context, url: String, trusted: Boolean = false) {
            val level = if (trusted) SourceLevel.TRUSTED else SourceLevel.UNKNOWN
            context.startActivity(
                Intent(context, WebViewActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_SOURCE_LEVEL, level.name)
            )
        }
    }
}
