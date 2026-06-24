package com.novamind.app.common.pdf

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.novamind.app.ui.theme.AppTheme

/**
 * PDF 预览测试页（仅供 Debug 工具箱跳转）。
 * 选择本地 PDF，用 Android 原生 [android.graphics.pdf.PdfRenderer] 逐页渲染为位图展示。
 */
class PdfViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialPath = intent.getStringExtra(EXTRA_PATH)
        setContent {
            AppTheme {
                PdfViewerRoute(onBack = { finish() }, initialPath = initialPath)
            }
        }
    }

    companion object {
        private const val EXTRA_PATH = "extra_path"

        /** 打开空阅读器（自行选择文件，Debug 工具箱用）。 */
        fun start(context: Context) {
            context.startActivity(Intent(context, PdfViewerActivity::class.java))
        }

        /** 直接打开指定路径的 PDF（笔记内 PDF 块点开用）。 */
        fun start(context: Context, path: String) {
            context.startActivity(
                Intent(context, PdfViewerActivity::class.java).putExtra(EXTRA_PATH, path),
            )
        }
    }
}
