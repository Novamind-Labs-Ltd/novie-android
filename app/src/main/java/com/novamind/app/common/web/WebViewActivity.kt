package com.novamind.app.common.web

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.novamind.app.ui.theme.AppTheme

/**
 * 独立进程承载 WebView（清单声明 android:process=":web"）。
 * 网页/容器即使崩溃也只影响 ":web" 进程，不会带崩主进程。
 *
 * JSBridge 的来源等级由 [com.novamind.app.common.web.bridge.DomainWhitelist] 按 URL
 * 动态判定，调用方无需传入。
 */
class WebViewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        setContent {
            AppTheme {
                WebViewScreen(
                    url = url,
                    onBack = { finish() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    companion object {
        private const val EXTRA_URL = "extra_url"

        fun start(context: Context, url: String) {
            context.startActivity(
                Intent(context, WebViewActivity::class.java).putExtra(EXTRA_URL, url)
            )
        }
    }
}
