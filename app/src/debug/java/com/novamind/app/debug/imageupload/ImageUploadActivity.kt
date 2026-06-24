package com.novamind.app.debug.imageupload

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.novamind.app.ui.theme.AppTheme

/**
 * 图片上传测试页（仅供 Debug 工具箱跳转）。
 * 选择本地图片后，以 multipart/form-data 上传到可配置的接口地址并展示响应。
 */
class ImageUploadActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                ImageUploadRoute(onBack = { finish() })
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, ImageUploadActivity::class.java))
        }
    }
}
