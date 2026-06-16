package com.novamind.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import kotlin.math.sqrt

private val BarBg = Color(0xFF1E1E1E)
private val WaveColor = Color(0xFFE9E9E9)
private val ControlBg = Color(0xFFFFFFFF)
private val SendGreen = Color(0xFF2E9E5B)
private const val WAVE_BARS = 48
private const val WAVE_BASELINE = 0.06f

/**
 * 录音条（点击工具栏「Voice」后出现）。自管计时与暂停状态。
 *
 * @param onCancel 取消录音（丢弃）
 * @param onConfirm 完成录音，回传时长（秒）
 *
 * 注：当前为录制 UI + 计时占位；真正音频采集（MediaRecorder + RECORD_AUDIO 权限）后续接入。
 */
@Composable
fun VoiceRecordingBar(
    onCancel: () -> Unit,
    onConfirm: (path: String, durationSeconds: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var paused by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }

    // 真实录音器：进入即开始，离开即释放
    val recorder = remember { com.novamind.app.common.audio.AudioRecorder(context) }
    var started by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }   // 已确认/取消，避免 onDispose 误删
    androidx.compose.runtime.DisposableEffect(Unit) {
        started = recorder.start()
        onDispose { if (!finished) recorder.cancel() }
    }

    // 计时：录音中且未暂停时每秒 +1
    LaunchedEffect(paused, started) {
        while (started && !paused) {
            kotlinx.coroutines.delay(1000)
            elapsed += 1
        }
    }

    // 波形振幅：定时读取麦克风最大振幅，滚动推入
    var levels by remember { mutableStateOf(List(WAVE_BARS) { WAVE_BASELINE }) }
    LaunchedEffect(paused, started) {
        while (started && !paused) {
            kotlinx.coroutines.delay(70)
            val amp = recorder.maxAmplitude()                 // 0..32767
            val norm = (amp / 18000f).coerceIn(0f, 1f)
            val level = sqrt(norm).coerceAtLeast(WAVE_BASELINE) // sqrt 让动态更自然
            levels = levels.drop(1) + level
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(BarBg)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            // 顶部：波形铺满整行 + 计时（右）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Waveform(
                    levels = levels,
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = formatTime(elapsed),
                    color = WaveColor,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(14.dp))

            // 底部：取消 / 暂停-继续 / 完成
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(
                    iconRes = R.drawable.ic_close,
                    desc = "取消",
                    bg = ControlBg,
                    tint = Color(0xFF1A1A1A),
                    onClick = {
                        finished = true
                        recorder.cancel()
                        onCancel()
                    },
                )
                RoundButton(
                    iconRes = if (paused) R.drawable.ic_play else R.drawable.ic_pause,
                    desc = if (paused) "继续" else "暂停",
                    bg = ControlBg,
                    tint = Color(0xFF1A1A1A),
                    onClick = {
                        paused = !paused
                        if (paused) recorder.pause() else recorder.resume()
                    },
                )
                RoundButton(
                    iconRes = R.drawable.ic_arrow_up,
                    desc = "完成",
                    bg = SendGreen,
                    tint = Color.White,
                    onClick = {
                        finished = true
                        val path = recorder.stop()
                        if (path != null) onConfirm(path, elapsed) else onCancel()
                    },
                )
            }
        }
    }
}

/** 按 [levels]（0..1，最新值在右侧）绘制随音量变化的波形。 */
@Composable
private fun Waveform(levels: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val barCount = levels.size
        if (barCount == 0) return@Canvas
        val gap = 3.5f
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        val maxH = size.height
        for (i in 0 until barCount) {
            val h = (maxH * levels[i]).coerceAtLeast(barWidth)
            val x = i * (barWidth + gap)
            val y = (maxH - h) / 2f
            drawRoundRect(
                color = WaveColor,
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

@Composable
private fun RoundButton(
    iconRes: Int,
    desc: String,
    bg: Color,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = tint),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = desc,
            tint = tint,
            modifier = Modifier.size(if (iconRes == R.drawable.ic_arrow_up) 24.dp else 22.dp),
        )
    }
}

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
