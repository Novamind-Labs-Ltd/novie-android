package com.novamind.app.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current

// 配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析（不使用硬编码颜色）
private val Bg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
private val TextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val TextSub: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
private val Accent: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Brand.default.current()
private val OnAccent: Color
    @Composable @ReadOnlyComposable get() = TextColors.Inverse.default.current()
private val ErrorColor: Color
    @Composable @ReadOnlyComposable get() = TextColors.Error.default.current()

/**
 * 登录页（无状态）。展示 [uiState]，交互通过 [onLogin] / [onDismissError] 上抛。
 */
@Composable
fun LoginScreen(
    uiState: AuthUiState,
    onLogin: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 错误出现后短暂展示，这里简单地在下次点击登录时清除（由 Route 决定具体策略）
    LaunchedEffect(uiState.errorMessage) { /* 预留：可接 Snackbar */ }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Bg),
    ) {
        Image(
            painter = painterResource(R.drawable.login_globe_grid),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(maxWidth * (238f / 412f)),
        )
        Image(
            painter = painterResource(R.drawable.login_globe_arcs),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(maxWidth * (211f / 412f)),
        )
        Image(
            painter = painterResource(R.drawable.login_novie_logo),
            contentDescription = "Novie",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset(
                    x = maxWidth * (279f / 412f),
                    y = maxHeight * (737f / 917f),
                )
                .size(
                    width = maxWidth * (86f / 412f),
                    height = maxWidth * (28f / 412f),
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.58f to Bg,
                        1f to Bg,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-56.5).dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(64.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(36.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Automatic",
                        fontSize = 24.sp,
                        lineHeight = 36.sp,
                        color = TextSub,
                    )
                    Text(
                        text = "Agent Flow",
                        fontSize = 24.sp,
                        lineHeight = 36.sp,
                        color = TextTitle,
                    )
                }
                Text(
                    text = "Let’s Do The\nUnthinkable",
                    fontSize = 48.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextTitle,
                )
                Text(
                    text = "Novie uses Simulation-Driven Development to align your team before a single line of code is written — turning specifications into working prototypes in minutes.",
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = TextTitle,
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Accent)
                        .clickable(
                            enabled = !uiState.isLoading,
                            onClick = {
                                if (uiState.errorMessage != null) onDismissError()
                                onLogin()
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            color = OnAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text(
                            text = "Get started",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = OnAccent,
                        )
                    }
                }

                if (uiState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
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
        uiState = AuthUiState(isCheckingSession = false, errorMessage = "Sign-in cancelled"),
        onLogin = {},
        onDismissError = {},
    )
}
