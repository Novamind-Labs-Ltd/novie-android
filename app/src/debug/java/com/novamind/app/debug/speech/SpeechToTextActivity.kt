package com.novamind.app.debug.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.novamind.app.ui.theme.AppTheme

/**
 * 语音转文字测试页（仅供 Debug 工具箱跳转）。
 * 使用 Android 原生 [android.speech.SpeechRecognizer] 实现实时语音转文字。
 */
class SpeechToTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                SpeechToTextRoute(onBack = { finish() })
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, SpeechToTextActivity::class.java))
        }
    }
}
