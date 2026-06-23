package com.novamind.app.debug.pdf

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
        setContent {
            AppTheme {
                PdfViewerRoute(onBack = { finish() })
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, PdfViewerActivity::class.java))
        }
    }
}
