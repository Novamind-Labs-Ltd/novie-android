package com.novamind.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.tooling.preview.Preview
import com.novamind.app.R
import com.novamind.app.common.config.AppConfig
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import kotlinx.coroutines.launch
import kotlin.math.sqrt

// 配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析（不使用硬编码颜色）。
// 视觉参考 Figma「new conversation · Dialog main」：浅色底部卡片 + 深色麦克风主按钮 + 品牌绿发送。
private val CardBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Interactive.tertiary.current()   // #fcfaf6
private val CardTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()               // 标题 / 计时 / 波形
private val CardLabel: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()               // 中间状态提示语
private val DividerColor: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.default.current()             // 提示语两侧分隔线
private val CancelBorder: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.strong.current()              // 取消按钮描边 #a3a3a3
private val CancelIcon: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val MicBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Primary.default.current()         // 深色麦克风主按钮
private val MicIcon: Color
    @Composable @ReadOnlyComposable get() = TextColors.Inverse.default.current()
private val SendBg: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Brand.default.current()               // 品牌绿 #1b6b45
private val SendIcon: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Success.text.current()                // 恒定白
// 禁用发送态（Figma 862:61264）：secondary disabled 半透明深色底 + 白色箭头。
private val DisabledBg: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Secondary.backgroundDisabled.current()
private val DisabledIcon: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Success.text.current()
private val CompactCancelBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Primary.secondary.current()        // Figma #f1f3f4
private val CompactCardBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()          // Figma surface/default: white
private val CompactTimer: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()               // Figma #656565
private val RecordingDot: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Error.default.current()            // 录音中红点
private val PausedDot: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.tertiary.current()               // 暂停灰点
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
// 与设计稿一致：308dp 宽内 45 根竖线，中心距约 7dp（见 Figma new conversation · recording 波形）
private const val WAVE_BARS = 45
private val WAVE_WIDTH = 308.dp
private val WAVE_HEIGHT = 42.dp
private val WAVE_BAR_WIDTH = 2.dp
private const val WAVE_BASELINE = 0.06f
// 累计「有声」时长低于此值（毫秒）视为「没有声音」（集中配置见 AppConfig.Media）
private const val MIN_VOICED_MS = AppConfig.Media.MIN_VOICED_MS
// 录音时长不足此秒数时禁止发送（集中配置见 AppConfig.Media）
private const val MIN_RECORD_SECONDS = AppConfig.Media.MIN_RECORD_SECONDS

/**
 * 录音卡片阶段：
 * - [Idle] 未开始（「Tap the mic to start」，点中间麦克风开始）
 * - [Recording] 录制中（可暂停 / 继续）
 * - [Sending] 上传中（Send 后等待结果）
 * - [UploadFailed] 上传失败（可重试）
 */
private enum class RecordingBarPhase { Idle, Recording, Sending, UploadFailed }

/**
 * 录音卡片（点击工具栏「Voice」后从底部升起）。
 *
 * 计时 / 暂停 / 振幅等真实状态由前台服务 [com.novamind.app.common.audio.RecordingService]
 * 持有并回写到 [com.novamind.app.common.audio.RecordingController]，因此锁屏 / 切后台时
 * 录音不中断，且会在通知栏 / 锁屏常驻一条录音通知。本组件只负责观察状态、下发指令。
 *
 * 交互（对齐 Figma）：进入即为空闲态，点中间麦克风开始录音；录音中中间按钮切换为暂停 / 继续，
 * 左侧「×」丢弃，右侧品牌绿「↑」发送。
 *
 * @param onCancel 取消录音（丢弃 / 空闲态直接关闭）
 * @param onConfirm 完成录音（上传成功后）回传路径与时长（秒）
 * @param onUpload 可选上传步骤：录音落盘后调用，返回 false 进入失败态（绿色重试按钮，
 *   可重传同一文件）；为 null 时跳过上传直接 [onConfirm]（当前 CreateScreen 本地插入即此路径）。
 * @param compact 紧凑输入框模式（Ask Novie 使用）；为 false 时保持 Create 页的大型录音面板。
 * @param autoStart 组件进入后是否立即开始录音。
 */
@Composable
fun VoiceRecordingBar(
    onCancel: () -> Unit,
    onConfirm: (path: String, durationSeconds: Int) -> Unit,
    modifier: Modifier = Modifier,
    onUpload: (suspend (path: String, durationSeconds: Int) -> Boolean)? = null,
    compact: Boolean = false,
    autoStart: Boolean = false,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val snapshot by com.novamind.app.common.audio.RecordingController.state
        .collectAsStateWithLifecycle()

    val paused = snapshot.paused
    val elapsed = snapshot.elapsedSeconds

    // 是否已点麦克风开始录音（false = 空闲态「Tap the mic to start」）
    var started by remember { mutableStateOf(false) }
    var showNoVoice by remember { mutableStateOf(false) }
    // 点 Send 后的上传中状态：停止指令已下发、等待服务回写结果 / 上传中。期间全部控件禁用。
    var sending by remember { mutableStateOf(false) }
    // 上传失败：保留已落盘的录音（path, duration），显示重试按钮可重传。
    var pendingUpload by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 执行上传：成功 → onConfirm；失败 → 进入失败态（保留文件待重试）。
    suspend fun uploadAndConfirm(path: String, durationSeconds: Int) {
        val ok = onUpload?.invoke(path, durationSeconds) ?: true
        if (ok) {
            pendingUpload = null
            com.novamind.app.common.audio.RecordingController.reset()
            onConfirm(path, durationSeconds)
        } else {
            sending = false
            pendingUpload = path to durationSeconds
        }
    }
    // 点删除后的「丢弃录音」二次确认
    var showDiscardConfirm by remember { mutableStateOf(false) }
    // 是否因弹确认而主动暂停（用于「Keep recording」时恢复；本就暂停则保持暂停）
    var pausedForConfirm by remember { mutableStateOf(false) }

    // 波形振幅：定时读取服务上报的最大振幅，滚动推入
    var levels by remember { mutableStateOf(List(WAVE_BARS) { WAVE_BASELINE }) }

    // 点中间麦克风开始（或「Try again」重新）录音
    fun startRecording() {
        levels = List(WAVE_BARS) { WAVE_BASELINE }
        showNoVoice = false
        sending = false
        pendingUpload = null
        com.novamind.app.common.audio.RecordingController.reset()
        com.novamind.app.common.audio.RecordingService.start(context)
        started = true
    }

    // 调用方明确要求自动开始时，组件进入后直接开始采集。
    LaunchedEffect(autoStart) {
        if (autoStart && !started) startRecording()
    }

    // 进入时先复位共享快照；离开页面时若仍在录音（用户直接返回）则取消丢弃。
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.novamind.app.common.audio.RecordingController.reset()
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
                // consumeResult 会改变 LaunchedEffect 的 key；上传放到稳定的组件作用域，
                // 避免当前 Effect 重启时连带取消 Retrofit 请求。
                result.voicedMs >= MIN_VOICED_MS -> scope.launch {
                    uploadAndConfirm(result.path, result.durationSeconds)
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

    // 波形振幅：定时读取服务上报的最大振幅，滚动推入（仅录音中）
    LaunchedEffect(started, paused, showNoVoice) {
        while (started && !paused && !showNoVoice) {
            kotlinx.coroutines.delay(70)
            val amp = com.novamind.app.common.audio.RecordingController.state.value.amplitude
            val norm = (amp / 18000f).coerceIn(0f, 1f)
            val level = sqrt(norm).coerceAtLeast(WAVE_BASELINE) // sqrt 让动态更自然
            levels = levels.drop(1) + level
        }
    }

    val phase = when {
        pendingUpload != null -> RecordingBarPhase.UploadFailed
        sending -> RecordingBarPhase.Sending
        !started -> RecordingBarPhase.Idle
        else -> RecordingBarPhase.Recording
    }
    RecordingBarContent(
        levels = levels,
        elapsed = pendingUpload?.second ?: elapsed,
        paused = paused,
        phase = phase,
        sendEnabled = elapsed >= MIN_RECORD_SECONDS,   // 不足 3 秒禁止发送
        onCancelClick = {
            when (phase) {
                // 空闲态：还没录，直接关闭（无需二次确认）
                RecordingBarPhase.Idle -> onCancel()
                // 录音中：先暂停再弹二次确认（本就暂停则保持）
                RecordingBarPhase.Recording -> {
                    if (!paused) {
                        com.novamind.app.common.audio.RecordingService.pause(context)
                        pausedForConfirm = true
                    }
                    showDiscardConfirm = true
                }
                // 失败态：录音已停止，弹确认后删除待重传文件
                RecordingBarPhase.UploadFailed -> showDiscardConfirm = true
                RecordingBarPhase.Sending -> Unit
            }
        },
        onMic = {
            when {
                !started -> startRecording()                                              // 空闲 → 开始
                paused -> com.novamind.app.common.audio.RecordingService.resume(context)  // 暂停 → 继续
                else -> com.novamind.app.common.audio.RecordingService.pause(context)     // 录音 → 暂停
            }
        },
        onSend = {
            // 进入上传中：停止由服务下发，完成 / 无声判定在状态回写后统一处理
            sending = true
            com.novamind.app.common.audio.RecordingService.stop(context)
        },
        onRetry = {
            // 重传同一文件：回到上传中，结果仍走 uploadAndConfirm 分支
            pendingUpload?.let { (path, duration) ->
                sending = true
                pendingUpload = null
                scope.launch { uploadAndConfirm(path, duration) }
            }
        },
        compact = compact,
        modifier = modifier,
    )

    if (showNoVoice) {
        NoVoiceDialog(onTryAgain = { startRecording() })
    }

    // 删除录音二次确认（居中弹窗，复用通用 AppAlertDialog）：Discard 才真正丢弃，Keep 继续录音
    if (showDiscardConfirm) {
        AppAlertDialog(
            // Keep / 点遮罩 / 返回键：若是为确认而暂停的，则恢复录音
            onDismissRequest = {
                showDiscardConfirm = false
                if (pausedForConfirm) {
                    com.novamind.app.common.audio.RecordingService.resume(context)
                    pausedForConfirm = false
                }
            },
            title = "Discard recording?",
            message = "This recording will be permanently deleted.",
            confirmLabel = "Discard",
            dismissLabel = "Keep",
            onConfirm = {
                showDiscardConfirm = false
                pausedForConfirm = false
                val pending = pendingUpload
                if (pending != null) {
                    // 失败态：录音服务已停止，直接删除待重传文件并退出
                    runCatching { java.io.File(pending.first).delete() }
                    pendingUpload = null
                    com.novamind.app.common.audio.RecordingController.reset()
                    onCancel()
                } else {
                    // Discard：取消录音（handleCancel → recorder.cancel 会删除录音源文件）
                    com.novamind.app.common.audio.RecordingService.cancel(context)
                }
            },
        )
    }
}

/**
 * 录音卡片的**无状态**内容层（对齐 Figma「Dialog main」）：
 * 标题行（Voice note · 计时）→ 状态行（提示语 / 波形，两侧分隔线）→ 操作行（×｜麦克风｜↑）。
 * 与录音服务解耦，供 [VoiceRecordingBar] 复用并可直接 @Preview。
 *
 * @param onMic 中间麦克风主按钮：空闲开始 / 录音暂停 / 暂停继续，由调用方按状态分派。
 * @param phase [RecordingBarPhase.Sending] 上传中：发送按钮变 loading，全部控件禁用；
 *   [RecordingBarPhase.UploadFailed] 上传失败：右侧变绿色重试（重传同一段录音）。
 */
@Composable
private fun RecordingBarContent(
    levels: List<Float>,
    elapsed: Int,
    paused: Boolean,
    sendEnabled: Boolean,
    onCancelClick: () -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    phase: RecordingBarPhase = RecordingBarPhase.Idle,
    onRetry: () -> Unit = {},
    compact: Boolean = false,
) {
    if (compact) {
        CompactRecordingBarContent(
            levels = levels,
            elapsed = elapsed,
            paused = paused,
            sendEnabled = sendEnabled,
            phase = phase,
            onCancelClick = onCancelClick,
            onSend = onSend,
            onRetry = onRetry,
            modifier = modifier,
        )
        return
    }

    val cardShape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = cardShape, clip = false)
            .clip(cardShape)
            .background(CardBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 24.dp, bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            // 标题行：状态（左，录音中为闪烁红点 + Recording） / 计时（右）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RecordingStatusLabel(phase = phase, paused = paused)
                Text(
                    text = formatTime(elapsed),
                    color = CardTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(22.dp))

            // 状态行：录音中显示波形，其余显示提示语（两侧分隔线）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (phase == RecordingBarPhase.Recording) {
                    // 录音中：波形居中（宽度对齐设计稿 308dp）；暂停时波形冻结并略微变淡
                    Waveform(
                        levels = levels,
                        color = CardTitle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = WAVE_WIDTH)
                            .height(WAVE_HEIGHT)
                            .alpha(if (paused) 0.4f else 1f),
                    )
                } else {
                    val label = when (phase) {
                        RecordingBarPhase.Idle -> "Tap the mic to start"
                        RecordingBarPhase.Sending -> "Sending…"
                        RecordingBarPhase.UploadFailed -> "Upload failed"
                        RecordingBarPhase.Recording -> ""
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Box(Modifier.weight(1f).height(1.dp).background(DividerColor))
                        Text(
                            text = label,
                            color = CardLabel,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                        Box(Modifier.weight(1f).height(1.dp).background(DividerColor))
                    }
                }
            }

            Spacer(Modifier.height(26.dp))

            // 操作行：取消（×，描边）｜麦克风（深色主按钮）｜发送 / 重试（品牌绿）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                    CircleButton(
                        iconRes = R.drawable.ic_close,
                        desc = "Cancel",
                        buttonSize = 64.dp,
                        bg = null,
                        borderColor = CancelBorder,
                        tint = CancelIcon,
                        enabled = phase != RecordingBarPhase.Sending,
                        onClick = onCancelClick,
                    )
                }

                // 中间主按钮：空闲 / 暂停继续显示麦克风，录音中显示暂停
                val micIcon = when {
                    phase == RecordingBarPhase.Recording && !paused -> R.drawable.ic_pause
                    phase == RecordingBarPhase.Recording && paused -> R.drawable.ic_play
                    else -> R.drawable.ic_mic
                }
                CircleButton(
                    iconRes = micIcon,
                    desc = when {
                        phase == RecordingBarPhase.Idle -> "Start recording"
                        phase == RecordingBarPhase.Recording && !paused -> "Pause"
                        phase == RecordingBarPhase.Recording && paused -> "Resume"
                        else -> "Microphone"
                    },
                    buttonSize = 84.dp,
                    iconSize = 32.dp,
                    bg = MicBg,
                    tint = MicIcon,
                    elevated = true,
                    enabled = phase == RecordingBarPhase.Idle || phase == RecordingBarPhase.Recording,
                    onClick = onMic,
                )

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                    if (phase == RecordingBarPhase.UploadFailed) {
                        CircleButton(
                            iconRes = R.drawable.ic_refresh,
                            desc = "Retry upload",
                            buttonSize = 64.dp,
                            bg = SendBg,
                            tint = SendIcon,
                            onClick = onRetry,
                        )
                    } else {
                        val sending = phase == RecordingBarPhase.Sending
                        CircleButton(
                            iconRes = R.drawable.ic_arrow_up,
                            desc = if (sending) "Uploading" else "Send",
                            buttonSize = 64.dp,
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
    }
}

/**
 * Ask Novie 输入框内的录音态（Figma 1166:70053 / 1166:63760）：取消按钮 + 动态波形 + 时长 + 发送按钮。
 * 录音生命周期仍由 [VoiceRecordingBar] 管理，这里只负责紧凑布局。
 */
@Composable
private fun CompactRecordingBarContent(
    levels: List<Float>,
    elapsed: Int,
    paused: Boolean,
    sendEnabled: Boolean,
    phase: RecordingBarPhase,
    onCancelClick: () -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val navigationBarHeight = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    // Figma 1166:70054：键盘收起态容器底部留白 24dp。
    // 系统导航栏 inset 已占用的部分需要扣除，确保不同导航模式下总间距至少为 24dp。
    val keyboardHiddenBottomPadding = if (imeVisible) {
        0.dp
    } else {
        (24.dp - navigationBarHeight).coerceAtLeast(0.dp)
    }
    androidx.compose.material3.Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .imePadding()
            .navigationBarsPadding()
            .padding(bottom = keyboardHiddenBottomPadding),
        shape = shape,
        color = CompactCardBg,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(
                iconRes = R.drawable.ic_close,
                desc = "Cancel recording",
                buttonSize = 36.dp,
                iconSize = 24.dp,
                bg = CompactCancelBg,
                tint = CancelIcon,
                enabled = phase != RecordingBarPhase.Sending,
                onClick = onCancelClick,
            )

            if (phase == RecordingBarPhase.Recording) {
                Waveform(
                    levels = levels,
                    color = CardTitle,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .alpha(if (paused) 0.4f else 1f),
                )
            } else {
                Text(
                    text = when (phase) {
                        RecordingBarPhase.Idle -> "Starting…"
                        RecordingBarPhase.Sending -> "Sending…"
                        RecordingBarPhase.UploadFailed -> "Upload failed"
                        RecordingBarPhase.Recording -> ""
                    },
                    modifier = Modifier.weight(1f),
                    color = CardLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }

            Text(
                text = formatCompactTime(elapsed),
                color = CompactTimer,
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
            )

            if (phase == RecordingBarPhase.UploadFailed) {
                CircleButton(
                    iconRes = R.drawable.ic_refresh,
                    desc = "Retry upload",
                    buttonSize = 36.dp,
                    iconSize = 24.dp,
                    bg = SendBg,
                    tint = SendIcon,
                    onClick = onRetry,
                )
            } else {
                val sending = phase == RecordingBarPhase.Sending
                CircleButton(
                    iconRes = R.drawable.ic_arrow_up,
                    desc = if (sending) "Uploading" else "Send recording",
                    buttonSize = 36.dp,
                    iconSize = 24.dp,
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

/**
 * 标题行左侧状态：录音中显示「红点 + Recording」，红点在录音时一闪一闪（暂停时静止变淡），
 * 其余阶段仅显示文案（空闲 Voice note / 暂停 Paused / 上传中 Voice note …）。
 */
@Composable
private fun RecordingStatusLabel(phase: RecordingBarPhase, paused: Boolean) {
    val recording = phase == RecordingBarPhase.Recording
    val transition = rememberInfiniteTransition(label = "rec-dot")
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rec-dot-alpha",
    )
    val statusText = when {
        recording && paused -> "Paused"
        recording -> "Recording"
        else -> "Voice note"
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (recording) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .alpha(if (paused) 1f else blink)     // 录音中红点闪烁；暂停灰点静止
                    .clip(CircleShape)
                    .background(if (paused) PausedDot else RecordingDot),
            )
        }
        Text(
            text = statusText,
            color = CardTitle,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
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
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "We couldn't hear any audio. Please check your microphone, " +
                    "ensure you're in a quiet environment, and try again.",
                color = DialogBody,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
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
                    fontWeight = FontWeight.Medium,
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
        // 细竖线固定宽度（~2dp），剩余空间均分为间距 → 中心距约 7dp，与设计稿一致
        val barWidth = WAVE_BAR_WIDTH.toPx()
        val gap = if (barCount > 1) (size.width - barWidth * barCount) / (barCount - 1) else 0f
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

/**
 * 圆形按钮：可填充背景（[bg]）或描边（[borderColor]），支持 [loading] 进行中态与 [elevated] 投影。
 * [loading] 是「进行中」而非「禁用」：背景保持全亮，仅不可点击。
 */
@Composable
private fun CircleButton(
    iconRes: Int,
    desc: String,
    buttonSize: Dp,
    bg: Color?,
    tint: Color,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    iconSize: Dp = 24.dp,
    elevated: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(buttonSize)
            // 禁用时不投影，与置灰的底色/图标一致，避免看起来仍是可用的悬浮按钮
            .then(if (elevated && enabled) Modifier.shadow(6.dp, CircleShape) else Modifier)
            .clip(CircleShape)
            .then(
                if (bg != null) {
                    Modifier.background(if (enabled || loading) bg else DisabledBg)
                } else {
                    Modifier
                },
            )
            .then(
                if (borderColor != null) {
                    Modifier.border(
                        1.dp,
                        if (enabled) borderColor else borderColor.copy(alpha = 0.4f),
                        CircleShape,
                    )
                } else {
                    Modifier
                },
            )
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
                modifier = Modifier.size(24.dp),
            )
        } else {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = desc,
                // 有底色的按钮禁用时用中性灰图标；描边/无底按钮维持原色淡化
                tint = when {
                    enabled -> tint
                    bg != null -> DisabledIcon
                    else -> tint.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

/** 紧凑输入框中的计时格式（Figma：0:01）。 */
private fun formatCompactTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

// 预览用假波形：正弦叠加基线，模拟录音中的动态起伏。
private fun previewLevels(): List<Float> =
    List(WAVE_BARS) { i ->
        (WAVE_BASELINE + 0.75f * kotlin.math.abs(kotlin.math.sin(i / 3.5f))).coerceAtMost(1f)
    }

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Recording card · Idle (tap mic to start)")
@Composable
private fun RecordingBarIdlePreview() {
    AppTheme {
        RecordingBarContent(
            levels = List(WAVE_BARS) { WAVE_BASELINE },
            elapsed = 0,
            paused = false,
            sendEnabled = false,
            phase = RecordingBarPhase.Idle,
            onCancelClick = {},
            onMic = {},
            onSend = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Recording card · Recording")
@Composable
private fun RecordingBarRecordingPreview() {
    AppTheme {
        RecordingBarContent(
            levels = previewLevels(),
            elapsed = 23,
            paused = false,
            sendEnabled = true,
            phase = RecordingBarPhase.Recording,
            onCancelClick = {},
            onMic = {},
            onSend = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Recording card · Paused")
@Composable
private fun RecordingBarPausedPreview() {
    AppTheme {
        RecordingBarContent(
            levels = previewLevels(),
            elapsed = 75,
            paused = true,
            sendEnabled = true,
            phase = RecordingBarPhase.Recording,
            onCancelClick = {},
            onMic = {},
            onSend = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Recording card · Uploading")
@Composable
private fun RecordingBarSendingPreview() {
    AppTheme {
        RecordingBarContent(
            levels = previewLevels(),
            elapsed = 42,
            paused = false,
            sendEnabled = true,
            phase = RecordingBarPhase.Sending,
            onCancelClick = {},
            onMic = {},
            onSend = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Recording card · Upload failed (retryable)")
@Composable
private fun RecordingBarUploadFailedPreview() {
    AppTheme {
        RecordingBarContent(
            levels = previewLevels(),
            elapsed = 8,
            paused = false,
            sendEnabled = true,
            phase = RecordingBarPhase.UploadFailed,
            onCancelClick = {},
            onMic = {},
            onSend = {},
            onRetry = {},
        )
    }
}
