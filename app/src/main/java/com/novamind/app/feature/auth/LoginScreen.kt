package com.novamind.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFFFBFAF7)
private val TextTitle = Color(0xFF1A1A1A)
private val TextSub = Color(0xFF6B6B6B)
private val Accent = Color(0xFF3D7A5A)
private val ErrorColor = Color(0xFFB3261E)

/**
 * 登录页（无状态）。展示 [uiState]，交互通过 [onLogin] / [onDismissError] 上抛。
 */
@Composable
fun LoginScreen(
    uiState: AuthUiState,
    onLogin: () -> Unit,
    onSkipLogin: () -> Unit = {},
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 错误出现后短暂展示，这里简单地在下次点击登录时清除（由 Route 决定具体策略）
    LaunchedEffect(uiState.errorMessage) { /* 预留：可接 Snackbar */ }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Logo 占位
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("N", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(20)
            Text("欢迎使用 Novamind", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextTitle)
            Spacer(8)
            Text(
                "登录以同步你的笔记与设置",
                fontSize = 14.sp,
                color = TextSub,
            )

            Spacer(40)

            Button(
                onClick = onLogin,
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text("登录 / 注册", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
                }
            }

            Spacer(8)

            // 免登录（游客模式）：不登录直接进入应用
            TextButton(
                onClick = onSkipLogin,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("免登录，先逛逛", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextSub)
            }

            if (uiState.errorMessage != null) {
                Spacer(16)
                Text(
                    text = uiState.errorMessage,
                    color = ErrorColor,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ErrorColor.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Spacer(dp: Int) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(dp.dp))
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    LoginScreen(uiState = AuthUiState(isCheckingSession = false), onLogin = {}, onDismissError = {})
}

@Preview(showBackground = true, name = "Loading")
@Composable
private fun LoginScreenLoadingPreview() {
    LoginScreen(
        uiState = AuthUiState(isCheckingSession = false, isLoading = true),
        onLogin = {},
        onDismissError = {},
    )
}

@Preview(showBackground = true, name = "Error")
@Composable
private fun LoginScreenErrorPreview() {
    LoginScreen(
        uiState = AuthUiState(isCheckingSession = false, errorMessage = "登录被取消"),
        onLogin = {},
        onDismissError = {},
    )
}
