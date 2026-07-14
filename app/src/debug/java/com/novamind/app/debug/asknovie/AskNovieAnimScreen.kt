package com.novamind.app.debug.asknovie

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.novamind.app.R
import kotlinx.coroutines.delay

// ── Debug 演示配色（自包含，不引主题令牌，便于对齐设计稿）──
private val ScreenBg = Color(0xFFEFEDE7)
private val PillBg = Color(0xFF1E1E1E)
private val PillText = Color(0xFFFFFFFF)
private val TextMain = Color(0xFF1A1A1A)
private val TextSub = Color(0xFF6B6B6B)
private val Accent = Color(0xFF3D7A5A)

/** 揭示动画的三个相位：静置图标 → 星尘浮现 → 展开「Ask Novie」。 */
private enum class RevealPhase { Idle, Dust, Expanded }

/**
 * 「Ask Novie」动画演示页（仅 Debug 工具箱跳转）。
 *
 * 星尘/星系光晕用 **Lottie** 播放（素材 `res/raw/ask_novie_galaxy.json`，
 * 灰阶点阵旋涡；换素材直接替换该文件即可，代码无需改动）——不手绘粒子。
 * 药丸按钮的「展开」与光晕的「浮现」由 Compose 动画驱动，与 Lottie 循环解耦。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskNovieAnimRoute(onBack: () -> Unit) {
    // Lottie 合成：灰阶点阵旋涡星系；换素材原地替换 res/raw 该文件即可。
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.ask_novie_galaxy),
    )

    var loop by remember { mutableStateOf(true) }
    var speed by remember { mutableStateOf(1f) }
    var phase by remember { mutableStateOf(RevealPhase.Idle) }
    // 自动回放触发器：递增即重跑一次「静置→星尘→展开」序列。
    var replayKey by remember { mutableIntStateOf(0) }

    // Lottie 星尘循环播放（与揭示相位无关，持续旋转）。
    val dustProgress by animateLottieCompositionAsState(
        composition = composition,
        iterations = if (loop) LottieConstants.IterateForever else 1,
        isPlaying = true,
        speed = speed,
        restartOnPlay = false,
    )

    // 揭示序列：进入页面或点「Replay」时跑一遍。
    LaunchedEffect(replayKey) {
        phase = RevealPhase.Idle
        delay(350)
        phase = RevealPhase.Dust
        delay(550)
        phase = RevealPhase.Expanded
    }

    // 星尘可见度/尺度随相位平滑过渡。
    val dustAlpha by animateFloatAsState(
        targetValue = when (phase) {
            RevealPhase.Idle -> 0f
            RevealPhase.Dust -> 0.6f
            RevealPhase.Expanded -> 1f
        },
        animationSpec = tween(600, easing = LinearEasing),
        label = "dustAlpha",
    )
    val dustScale by animateFloatAsState(
        targetValue = when (phase) {
            RevealPhase.Idle -> 0.6f
            RevealPhase.Dust -> 0.85f
            RevealPhase.Expanded -> 1f
        },
        animationSpec = tween(600),
        label = "dustScale",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg),
    ) {
        // 顶部栏：返回 + 标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "Back",
                tint = TextMain,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onBack)
                    .padding(8.dp)
                    .size(24.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text("Ask Novie · Animation", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextMain)
        }

        // 舞台：星尘光晕 + 药丸按钮居中
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            // Lottie 星尘光晕（药丸后方）。alpha/scale 由揭示相位驱动。
            LottieAnimation(
                composition = composition,
                progress = { dustProgress },
                modifier = Modifier
                    .size(320.dp)
                    .graphicsLayer {
                        alpha = dustAlpha
                        scaleX = dustScale
                        scaleY = dustScale
                    },
            )
            AskNoviePill(expanded = phase == RevealPhase.Expanded)
        }

        // 控制区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Controls", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Accent)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DemoChip("▶ Replay reveal") { replayKey++ }
                DemoChip("Idle") { phase = RevealPhase.Idle }
                DemoChip("Dust") { phase = RevealPhase.Dust }
                DemoChip("Expanded") { phase = RevealPhase.Expanded }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Loop dust", fontSize = 14.sp, color = TextMain)
                Switch(checked = loop, onCheckedChange = { loop = it })
            }

            Column {
                Text("Speed  ${"%.2f".format(speed)}x", fontSize = 14.sp, color = TextMain)
                Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.25f..2f)
            }

            Text(
                "Asset: res/raw/ask_novie_galaxy.json (grayscale dotted galaxy) — replace this file " +
                    "to swap the effect; no code change needed.",
                fontSize = 12.sp,
                color = TextSub,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/** 「Ask Novie」药丸按钮：折叠只显图标，展开显图标 + 文案，宽度平滑过渡。 */
@Composable
private fun AskNoviePill(expanded: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(PillBg)
            .animateContentSize()
            .padding(horizontal = if (expanded) 20.dp else 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chat),
            contentDescription = "Ask Novie",
            tint = PillText,
            modifier = Modifier.size(22.dp),
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(220)) + expandHorizontally(tween(260)),
            exit = fadeOut(tween(160)) + shrinkHorizontally(tween(200)),
        ) {
            Text("Ask Novie", color = PillText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DemoChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 13.sp,
        color = Accent,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Accent.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
