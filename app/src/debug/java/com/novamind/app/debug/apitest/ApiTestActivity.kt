package com.novamind.app.debug.apitest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.novamind.app.ui.theme.AppTheme

/**
 * 接口测试页（仅供 Debug 工具箱跳转）。
 * 页内点击按钮发起网络请求并渲染返回数据。
 */
class ApiTestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val target = runCatching {
            ApiTarget.valueOf(intent.getStringExtra(EXTRA_TARGET) ?: ApiTarget.ITEMS.name)
        }.getOrDefault(ApiTarget.ITEMS)
        setContent {
            AppTheme {
                ApiTestRoute(onBack = { finish() }, target = target)
            }
        }
    }

    companion object {
        private const val EXTRA_TARGET = "extra_target"

        fun start(context: Context, target: ApiTarget = ApiTarget.ITEMS) {
            context.startActivity(
                Intent(context, ApiTestActivity::class.java)
                    .putExtra(EXTRA_TARGET, target.name)
            )
        }
    }
}
