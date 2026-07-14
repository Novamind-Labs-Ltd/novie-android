package com.novamind.app.debug.asknovie

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.novamind.app.ui.theme.AppTheme

/**
 * 「Ask Novie」动画演示页（仅供 Debug 工具箱跳转）。
 * 用 Lottie 播放星尘/星系光晕（占位素材，设计师可原地替换），演示药丸按钮的揭示动画。
 */
class AskNovieAnimActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AskNovieAnimRoute(onBack = { finish() })
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, AskNovieAnimActivity::class.java))
        }
    }
}
