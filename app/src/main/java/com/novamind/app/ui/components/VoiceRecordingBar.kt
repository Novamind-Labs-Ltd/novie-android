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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.tooling.preview.Preview
import com.novamind.app.R
import com.novamind.app.common.config.AppConfig
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import kotlin.math.sqrt

// 配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析（不使用硬编码颜色）
private val BarBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Primary.default.current()
private val OnBar: Color
    @Composable @ReadOnlyComposable get() = TextColors.Inverse.default.current()
private val ControlBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val ControlIcon: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val SendBg: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Success.background.current()
private val SendIcon: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Success.text.current()
private val DialogBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
private val DialogTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val DialogBody: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
private val DialogButtonBg: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Primary.background.current()
private val DialogButtonText: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Primary.text.current()
private const val WAVE_BARS = 48
private const val WAVE_BASELINE = 0.06f
// 峰值振幅低于此值（0..32767）视为「没有声音」（集中配置见 AppConfig.Media）
private const val NO_VOICE_THRESHOLD = AppConfig.Media.NO_VOICE_THRESHOLD
// 录音时长不足此秒数时禁止发送（集中配置见 AppConfig.Media）
private const val MIN_RECORD_SECONDS = AppConfig.Media.MIN_RECORD_SECONDS

/**
 * 录音条（点击工具栏「Voice」后出现）。
 *
 * 计时 / 暂停 / 振幅等真实状态由前台服务 [com.novamind.app.common.audio.RecordingService]
 * 持有并回写到 [com.novamind.app.common.audio.RecordingController]，因此锁屏 / 切后台时
 * 录音不中断，且会在通知栏 / 锁屏常驻一条录音通知。本组件只负责观察状态、下发指令。
 *
 * @param onCancel 取消录音（丢弃）
 * @param onConfirm 完成录音，回传时长（秒）
 */
@Composable
fun VoiceRecordingBar(
    onCancel: () -> Unit,
    onConfirm: (path: String, durationSeconds: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val snapshot by com.novamind.app.common.audio.RecordingController.state
        .collectAsStateWithLifecycle()

    val paused = snapshot.paused
    val elapsed = snapshot.elapsedSeconds

    var started by remember { mutableStateOf(false) }
    var showNoVoice by remember { mutableStateOf(false) }
    // 点 Send 后的上传中状态：停止指令已下发、等待服务回写结果。期间全部控件禁用，不可继续录音。
    var sending by remember { mutableStateOf(false) }
    // 点删除后的「丢弃录音」二次确认
    var showDiscardConfirm by remember { mutableStateOf(false) }
    // 是否因弹确认而主动暂停（用于「Keep recording」时恢复；本就暂停则保持暂停）
    var pausedForConfirm by remember { mutableStateOf(false) }

    // 进入即启动前台录音服务；若离开页面时仍在录音（用户直接返回）则取消丢弃
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.novamind.app.common.audio.RecordingController.reset()
        com.novamind.app.common.audio.RecordingService.start(context)
        started = true
        onDispose {
            if (com.novamind.app.common.audio.RecordingController.state.value.active) {
                com.novamind.app.common.audio.RecordingService.cancel(context)
            }
        }
    }

    // 完成 / 取消的一次性事件统一在此处理（无论来自界面按钮还是通知操作）
    LaunchedEffect(snapshot.result, snapshot.cancelled) {
        val result = snapshot.result
        if (result != null) {
            com.novamind.app.common.audio.RecordingController.consumeResult()
            when {
                // 太短：丢弃（防御：通知栏「停止」可能在 3s 内触发）
                result.durationSeconds < MIN_RECORD_SECONDS -> {
                    runCatching { java.io.File(result.path).delete() }
                    com.novamind.app.common.audio.RecordingController.reset()
                    sending = false
                    onCancel()
                }
                result.peakAmplitude >= NO_VOICE_THRESHOLD -> {
                    com.novamind.app.common.audio.RecordingController.reset()
                    onConfirm(result.path, result.durationSeconds)
                }
                else -> {
                    runCatching { java.io.File(result.path).delete() }
                    sending = false
                    showNoVoice = true
                }
            }
        } else if (snapshot.cancelled && !showNoVoice) {
            com.novamind.app.common.audio.RecordingController.consumeCancelled()
            onCancel()
        }
    }

    // 波形振幅：定时读取服务上报的最大振幅，滚动推入
    var levels by remember { mutableStateOf(List(WAVE_BARS) { WAVE_BASELINE }) }
    LaunchedEffect(started, paused, showNoVoice) {
        while (started && !paused && !showNoVoice) {
            kotlinx.coroutines.delay(70)
            val amp = com.novamind.app.common.audio.RecordingController.state.value.amplitude
            val norm = (amp / 18000f).coerceIn(0f, 1f)
            val level = sqrt(norm).coerceAtLeast(WAVE_BASELINE) // sqrt 让动态更自然
            levels = levels.drop(1) + level
        }
    }

    // 重新录制（「Try again」）
    fun restartRecording() {
        levels = List(WAVE_BARS) { WAVE_BASELINE }
        showNoVoice = false
        sending = false
        com.novamind.app.common.audio.RecordingController.reset()
        com.novamind.app.common.audio.RecordingService.start(context)
    }

    RecordingBarContent(
        levels = levels,
        elapsed = elapsed,
        paused = paused,
        sending = sending,
        sendEnabled = elapsed >= MIN_RECORD_SECONDS,   // 不足 3 秒禁止发送
        onCancelClick = {
            // 点删除：先暂停录音，再弹二次确认；确认后才真正取消
            if (!paused) {
                com.novamind.app.common.audio.RecordingService.pause(context)
                pausedForConfirm = true
            }
            showDiscardConfirm = true
        },
        onPauseResume = {
            if (paused) {
                com.novamind.app.common.audio.RecordingService.resume(context)
            } else {
                com.novamind.app.common.audio.RecordingService.pause(context)
            }
        },
        onSend = {
            // 进入上传中：停止由服务下发，完成 / 无声判定在状态回写后统一处理
            sending = true
            com.novamind.app.common.audio.RecordingService.stop(context)
        },
        modifier = modifier,
    )

    if (showNoVoice) {
        NoVoiceDialog(onTryAgain = { restartRecording() })
    }

    // 删除录音二次确认（底部弹窗）：Discard 才真正取消（丢弃），Keep recording 继续录音
    if (showDiscardConfirm) {
        DeleteConfirmSheet(
            title = "Discard recording?",
            message = "This recording will be permanently deleted and cannot be recovered.",
            confirmLabel = "Discard",
            dismissLabel = "Keep recording",
            onConfirm = {
                // Discard：取消录音（handleCancel → recorder.cancel 会删除录音源文件）
                showDiscardConfirm = false
                pausedForConfirm = false
                com.novamind.app.common.audio.RecordingService.cancel(context)
            },
            onDismiss = {
                // Keep recording：若是为确认而暂停的，则恢复录音
                showDiscardConfirm = false
                if (pausedForConfirm) {
                    com.novamind.app.common.audio.RecordingService.resume(context)
                    pausedForConfirm = false
                }
            },
        )
    }
}

/**
 * 录音条的**无状态**内容层：波形 + 计时 + 取消/暂停-继续/完成。
 * 与录音服务解耦，供 [VoiceRecordingBar] 复用并可直接 @Preview。
 *
 * @param sending 上传中（点 Send 后等待结果回写）：发送按钮变 loading，全部控件禁用，不可继续录音。
 */
@Composable
private fun RecordingBarContent(
    levels: List<Float>,
    elapsed: Int,
    paused: Boolean,
    sendEnabled: Boolean,
    onCancelClick: () -> Unit,
    onPauseResume: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    sending: Boolean = false,
) {
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
                    color = OnBar,
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = formatTime(elapsed),
                    color = OnBar,
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
                    tint = ControlIcon,
                    enabled = !sending,
                    onClick = onCancelClick,
                )
                RoundButton(
                    iconRes = if (paused) R.drawable.ic_play else R.drawable.ic_pause,
                    desc = if (paused) "继续" else "暂停",
                    bg = ControlBg,
                    tint = ControlIcon,
                    enabled = !sending,
                    onClick = onPauseResume,
                )
                RoundButton(
                    iconRes = R.drawable.ic_arrow_up,
                    desc = if (sending) "上传中" else "完成",
                    bg = SendBg,
                    tint = SendIcon,
                    enabled = sendEnabled && !sending,
                    loading = sending,
                    onClick = onSend,
                )
            }
        }
    }
}

/** 「没有检测到声音」弹窗（底部弹出）。 */
@Composable
private fun NoVoiceDialog(onTryAgain: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onTryAgain,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(DialogBg)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "No voice detected",
                color = DialogTitle,
                fontSize = 20.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "We couldn't hear any audio. Please check your microphone, " +
                    "ensure you're in a quiet environment, and try again.",
                color = DialogBody,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(DialogButtonBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = DialogButtonText),
                        onClick = onTryAgain,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Try again",
                    color = DialogButtonText,
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                )
            }
        }
        }
    }
}

/** 按 [levels]（0..1，最新值在右侧）绘制随音量变化的波形。Canvas 非 @Composable，颜色由调用方解析传入。 */
@Composable
private fun Waveform(levels: List<Float>, color: Color, modifier: Modifier = Modifier) {
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
                color = color,
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
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            // loading 是「进行中」而非「禁用」：背景保持全亮，仅不可点击
            .background(if (enabled || loading) bg else bg.copy(alpha = 0.4f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = tint),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = tint,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = desc,
                tint = if (enabled) tint else tint.copy(alpha = 0.5f),
                modifier = Modifier.size(if (iconRes == R.drawable.ic_arrow_up) 24.dp else 22.dp),
            )
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

// 预览用假波形：正弦叠加基线，模拟录音中的动态起伏。
private fun previewLevels(): List<Float> =
    List(WAVE_BARS) { i ->
        (WAVE_BASELINE + 0.75f * kotlin.math.abs(kotlin.math.sin(i / 3.5f))).coerceAtMost(1f)
    }

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF7, name = "录音条 · 录制中")
@Composable
private fun RecordingBarRecordingPreview() {
    AppTheme {
        RecordingBarContent(
            levels = previewLevels(),
            elapsed = 23,
            paused = false,
            sendEnabled = true,
            onCancelClick = {},
            onPauseResume = {},
            onSend = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF7, name = "录音条 · 已暂停")
@Composable
private fun RecordingBarPausedPreview() {
    AppTheme {
        RecordingBarContent(
            levels = previewLevels(),
            elapsed = 75,
            paused = true,
            sendEnabled = true,
            onCancelClick = {},
            onPauseResume = {},
            onSend = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF7, name = "录音条 · 上传中")
@Composable
private fun RecordingBarSendingPreview() {
    AppTheme {
        RecordingBarContent(
            levels = previewLevels(),
            elapsed = 42,
            paused = false,
            sendEnabled = true,
            sending = true,
            onCancelClick = {},
            onPauseResume = {},
            onSend = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF7, name = "录音条 · 刚开始（不足3s禁止发送）")
@Composable
private fun RecordingBarJustStartedPreview() {
    AppTheme {
        RecordingBarContent(
            levels = List(WAVE_BARS) { WAVE_BASELINE },
            elapsed = 1,
            paused = false,
            sendEnabled = false,
            onCancelClick = {},
            onPauseResume = {},
            onSend = {},
        )
    }
}
