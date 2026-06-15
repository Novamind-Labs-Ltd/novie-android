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
        setContent {
            AppTheme {
                ApiTestRoute(onBack = { finish() })
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, ApiTestActivity::class.java))
        }
    }
}
