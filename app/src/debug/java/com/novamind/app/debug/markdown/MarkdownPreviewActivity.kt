package com.novamind.app.debug.markdown

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.novamind.app.ui.theme.AppTheme

/**
 * Markdown 阅读器测试页（仅供 Debug 工具箱跳转）。
 * 选择本地 .md 文件，用 mikepenz multiplatform-markdown-renderer 渲染预览。
 */
class MarkdownPreviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                MarkdownPreviewRoute(onBack = { finish() })
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, MarkdownPreviewActivity::class.java))
        }
    }
}
