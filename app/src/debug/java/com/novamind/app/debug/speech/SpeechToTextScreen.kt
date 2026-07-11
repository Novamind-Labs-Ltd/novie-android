package com.novamind.app.debug.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.theme.AppTheme

private val Bg = Color(0xFFF4F4F5)
private val Card = Color(0xFFFFFFFF)
private val TextMain = Color(0xFF1A1A1A)
private val TextSub = Color(0xFF6B6B6B)
private val Accent = Color(0xFF3D7A5A)
private val Danger = Color(0xFFD13C3C)

/**
 * 语音转文字控制器：封装 Android 原生 [SpeechRecognizer]，把回调状态投射为 Compose State。
 *
 * 说明：[SpeechRecognizer] 一次只识别「一句话」，说话停顿后即回调 [RecognitionListener.onResults]。
 * 这里用 [continuous] 实现「连续听写」——每次出结果/无匹配后自动重启监听，直到用户停止。
 * 所有回调都在主线程触发，可直接更新 State。
 */
class SpeechToTextController(private val context: Context) {

    /** 设备是否有可用的识别服务（无 Google 语音服务的设备会是 false）。 */
    var available by mutableStateOf(SpeechRecognizer.isRecognitionAvailable(context)); private set
    var listening by mutableStateOf(false); private set
    /** 连续听写：出结果后自动重启监听。 */
    var continuous by mutableStateOf(false)
    /** "device"=设备默认；或 "zh-CN" / "en-US"。 */
    var language by mutableStateOf("device")
    /** 当前一句的实时（未定稿）结果。 */
    var partial by mutableStateOf(""); private set
    /** 已定稿的累计文本。 */
    var transcript by mutableStateOf(""); private set
    /** 实时音量（dB，约 -2..10），用于电平指示。 */
    var rms by mutableStateOf(0f); private set
    var error by mutableStateOf<String?>(null); private set

    private var recognizer: SpeechRecognizer? = null
    // 用户意图：是否希望持续处于监听（用于区分「用户主动停止」与「单句自然结束」）
    private var active = false

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { error = null }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) { rms = rmsdB }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(code: Int) {
            partial = ""
            // 连续模式下，无匹配/超时属正常停顿，自动重启；其余视为错误并停止。
            if (active && continuous &&
                (code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            ) {
                restart()
            } else {
                listening = false
                active = false
                error = errorText(code)
            }
        }

        override fun onResults(results: Bundle?) {
            appendFinal(results)
            if (active && continuous) restart() else stopInternal()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partial = firstResult(partialResults)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /** 开始监听。调用前需已获得 RECORD_AUDIO 权限。 */
    fun start() {
        if (!available) { error = "No speech recognition service available on this device"; return }
        error = null
        active = true
        listening = true
        ensureRecognizer()
        runCatching { recognizer?.startListening(buildIntent()) }
            .onFailure { listening = false; active = false; error = it.message }
    }

    /** 用户主动停止（保留已识别文本）。 */
    fun stop() {
        active = false
        partial = ""
        runCatching { recognizer?.stopListening() }
        stopInternal()
    }

    fun clear() { transcript = ""; partial = "" }

    fun notifyPermissionDenied() { error = "Microphone permission is required for recognition" }

    /** 释放底层识别器（在页面销毁时调用）。 */
    fun destroy() {
        active = false
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun stopInternal() { listening = false; active = false }

    private fun appendFinal(results: Bundle?) {
        val text = firstResult(results)
        if (text.isNotBlank()) {
            transcript = if (transcript.isBlank()) text else "$transcript $text"
        }
        partial = ""
    }

    private fun restart() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.startListening(buildIntent()) }
    }

    private fun ensureRecognizer() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
        }
    }

    private fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        // 优先本地离线识别（无网络也可用，需系统已下载对应语言包）；不可用时系统自动回退在线。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        when (language) {
            "zh-CN" -> putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            "en-US" -> putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            else -> {}   // 设备默认语言
        }
    }

    private fun firstResult(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing recording permission"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy, please try again later"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Recognition failed (error code $code)"
    }
}

// ─── Route（有状态：连接控制器与权限）────────────────────────────────────────

@Composable
fun SpeechToTextRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { SpeechToTextController(context) }
    DisposableEffect(Unit) { onDispose { controller.destroy() } }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) controller.start() else controller.notifyPermissionDenied() }

    fun toggle() {
        if (controller.listening) {
            controller.stop()
        } else {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) controller.start() else permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    SpeechToTextScreen(
        available = controller.available,
        listening = controller.listening,
        continuous = controller.continuous,
        language = controller.language,
        partial = controller.partial,
        transcript = controller.transcript,
        rms = controller.rms,
        error = controller.error,
        onToggleListen = ::toggle,
        onToggleContinuous = { controller.continuous = !controller.continuous },
        onLanguage = { controller.language = it },
        onClear = { controller.clear() },
        onBack = onBack,
    )
}

// ─── Screen（无状态：纯 UI）──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpeechToTextScreen(
    available: Boolean,
    listening: Boolean,
    continuous: Boolean,
    language: String,
    partial: String,
    transcript: String,
    rms: Float,
    error: String?,
    onToggleListen: () -> Unit,
    onToggleContinuous: () -> Unit,
    onLanguage: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("Speech-to-Text Demo", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Back", tint = TextMain)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg, titleContentColor = TextMain),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (available) "Based on Android SpeechRecognizer (prefers local offline, falls back to online if language pack missing)"
                else "⚠️ No speech recognition service available on this device",
                fontSize = 12.sp,
                color = if (available) TextSub else Danger,
            )

            // 语言选择
            Column(
                modifier = Modifier.fillMaxWidth().background(Card, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Recognition Language", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LangChip("Device Default", language == "device") { onLanguage("device") }
                    LangChip("Chinese", language == "zh-CN") { onLanguage("zh-CN") }
                    LangChip("English", language == "en-US") { onLanguage("en-US") }
                    LangChip(if (continuous) "Continuous Dictation ✓" else "Continuous Dictation", continuous, onClick = onToggleContinuous)
                }
            }

            // 结果区
            Column(
                modifier = Modifier.fillMaxWidth().background(Card, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Recognition Result", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                val shown = buildString {
                    append(transcript)
                    if (partial.isNotBlank()) {
                        if (isNotBlank()) append(' ')
                        append(partial)
                    }
                }
                Text(
                    text = shown.ifBlank { "Tap the microphone below to start speaking…" },
                    fontSize = 16.sp,
                    color = if (shown.isBlank()) TextSub else TextMain,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (partial.isNotBlank()) {
                    Text("(Recognizing in real time…)", fontSize = 11.sp, color = TextSub)
                }
            }

            error?.let {
                Text(it, fontSize = 13.sp, color = Danger)
            }

            // 电平指示
            LevelBar(active = listening, rms = rms)

            // 控制按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MicButton(listening = listening, enabled = available, onClick = onToggleListen)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionChip(if (listening) "Stop" else "Start", primary = true, onClick = onToggleListen)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionChip("Copy") {
                            if (transcript.isNotBlank()) clipboard.setText(AnnotatedString(transcript))
                        }
                        ActionChip("Clear", danger = true, onClick = onClear)
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelBar(active: Boolean, rms: Float) {
    // rms 约 -2..10 dB，归一化到 0..1
    val level = if (active) ((rms + 2f) / 12f).coerceIn(0f, 1f) else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFE3E3E5)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(level)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (active) Accent else Color.Transparent),
        )
    }
}

@Composable
private fun MicButton(listening: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(if (listening) Danger else if (enabled) Accent else Color(0xFFBDBDBD))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mic),
            contentDescription = if (listening) "Stop" else "Start",
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun LangChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 13.sp,
        color = if (selected) Color.White else Accent,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Accent else Accent.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ActionChip(label: String, primary: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) Danger else Accent
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
        color = if (primary) Color.White else color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (primary) color else color.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SpeechToTextScreenPreview() {
    AppTheme {
        SpeechToTextScreen(
            available = true,
            listening = true,
            continuous = true,
            language = "zh-CN",
            partial = "today's weather",
            transcript = "This is a piece of text that has already been recognized.",
            rms = 6f,
            error = null,
            onToggleListen = {},
            onToggleContinuous = {},
            onLanguage = {},
            onClear = {},
            onBack = {},
        )
    }
}
