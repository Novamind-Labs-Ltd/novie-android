package com.novamind.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFFFBFAF7)
private val Accent = Color(0xFF3D7A5A)
private val TextTitle = Color(0xFF1A1A1A)
private val TextSub = Color(0xFF6B6B6B)

/**
 * 指纹解锁门控页。进入时自动弹一次系统生物识别框；用户取消/失败后可重试，
 * 或改用账号密码登录。
 *
 * @param isLoading 正在等待系统指纹框结果
 * @param errorMessage 上次失败提示（取消/不匹配等）
 * @param onUnlock 触发系统指纹框
 * @param onUsePassword 放弃指纹，改用 Auth0 账号登录
 */
@Composable
fun BiometricLockScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onUnlock: () -> Unit,
    onUsePassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 进入页面自动弹一次指纹框。
    LaunchedEffect(Unit) { onUnlock() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Bg)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("指纹登录", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextTitle)
        Spacer(Modifier.height(8.dp))
        Text(
            text = errorMessage ?: "请验证指纹以继续",
            fontSize = 14.sp,
            color = if (errorMessage != null) Color(0xFFB00020) else TextSub,
        )
        Spacer(Modifier.height(28.dp))

        if (isLoading) {
            CircularProgressIndicator(color = Accent)
        } else {
            Button(
                onClick = onUnlock,
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) {
                Text("使用指纹解锁")
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onUsePassword) {
            Text("改用账号登录", color = Accent)
        }
    }
}
