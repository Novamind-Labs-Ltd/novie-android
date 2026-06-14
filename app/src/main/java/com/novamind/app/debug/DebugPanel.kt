package com.novamind.app.debug

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.novamind.app.BuildConfig
import com.novamind.app.NovieApplication
import com.novamind.app.common.device.InstallId
import com.novamind.app.common.onboarding.OnboardingStore
import com.novamind.app.common.update.UpdateController
import com.novamind.app.common.update.UpdateType
import com.novamind.app.common.web.WebViewActivity
import com.novamind.app.common.web.bridge.SourceLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Bg = Color(0xFFF4F4F5)
private val Card = Color(0xFFFFFFFF)
private val TextMain = Color(0xFF1A1A1A)
private val TextSub = Color(0xFF6B6B6B)
private val Accent = Color(0xFF3D7A5A)
private val Danger = Color(0xFFD13C3C)

/**
 * Debug 工具箱面板（仅 Debug 包；摇一摇打开）。
 * 含：构建/设备信息、页面快速跳转、本地数据查看与清除、Feature Flag、应用内日志。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DebugPanel(
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as NovieApplication
    val scope = rememberCoroutineScope()

    val deviceId = remember {
        runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: "unknown"
    }
    val installUuid = remember { InstallId.get(context) }
    val screenInfo = remember {
        val dm = context.resources.displayMetrics
        "${dm.widthPixels} x ${dm.heightPixels} (${dm.densityDpi}dpi @${dm.density}x)"
    }

    var noteCount by remember { mutableStateOf(-1) }
    var recCount by remember { mutableStateOf(-1) }
    var refresh by remember { mutableStateOf(0) }

    // 组件/能力测试
    var urlInput by remember { mutableStateOf("https://m.bing.com") }
    // JSBridge 测试页强制来源等级（null = 按域名白名单）
    var bridgeLevel by remember { mutableStateOf<SourceLevel?>(null) }
    LaunchedEffect(refresh) {
        noteCount = runCatching { app.noteRepository.count() }.getOrDefault(-1)
        recCount = File(context.filesDir, "recordings").listFiles()?.size ?: 0
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Bg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🛠 Debug 工具箱", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextMain)

            // ── 信息 ──
            Section("信息") {
                InfoRow("应用", "${BuildConfig.APPLICATION_ID}")
                InfoRow("版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                InfoRow("构建类型", BuildConfig.BUILD_TYPE + if (BuildConfig.DEBUG) " · DEBUG" else "")
                InfoRow("Git", BuildConfig.GIT_SHA)
                InfoRow("打包时间", BuildConfig.BUILD_TIME)
                InfoRow("设备", "${Build.MANUFACTURER} ${Build.MODEL}")
                InfoRow("系统", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                InfoRow("deviceId", deviceId)
                InfoRow("UUID", installUuid)
                InfoRow("分辨率", screenInfo)
            }

            // ── 页面快速跳转 ──
            Section("页面快速跳转") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("home" to "Home", "create" to "Create", "library" to "Library",
                        "calendar" to "Calendar", "brand" to "Brand").forEach { (r, label) ->
                        Chip(label) { onNavigate(r) }
                    }
                }
            }

            // ── 本地数据 ──
            Section("本地数据") {
                InfoRow("笔记数", if (noteCount < 0) "…" else "$noteCount")
                InfoRow("录音数", if (recCount < 0) "…" else "$recCount")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("清空笔记", danger = true) {
                        scope.launch { app.noteRepository.clearAll(); refresh++ }
                    }
                    Chip("清空录音", danger = true) {
                        scope.launch {
                            withContext(Dispatchers.IO) { File(context.filesDir, "recordings").deleteRecursively() }
                            refresh++
                        }
                    }
                    Chip("清空图片/文件", danger = true) {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                File(context.filesDir, "note_images").deleteRecursively()
                                File(context.filesDir, "note_files").deleteRecursively()
                            }
                            refresh++
                        }
                    }
                }
            }

            // ── 组件 / 能力测试 ──
            Section("组件 / 能力测试") {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("打开 WebView") {
                        urlInput.trim().takeIf { it.isNotEmpty() }
                            ?.let { WebViewActivity.start(context, normalizeUrl(it)) }
                    }
                    Chip("example.com") { WebViewActivity.start(context, "https://example.com") }
                    Chip("Bing") { WebViewActivity.start(context, "https://m.bing.com") }
                    Chip("重置引导页") {
                        OnboardingStore.setCompleted(context, false)
                    }
                }
                // JSBridge：选来源等级 + 打开测试页（直观验证权限拦截 1003）
                Text(
                    "JSBridge 来源等级：" + (bridgeLevel?.name ?: "按域名白名单"),
                    fontSize = 12.sp, color = TextSub,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("按域名") { bridgeLevel = null }
                    Chip("TRUSTED") { bridgeLevel = SourceLevel.TRUSTED }
                    Chip("PARTNER") { bridgeLevel = SourceLevel.PARTNER }
                    Chip("UNKNOWN") { bridgeLevel = SourceLevel.UNKNOWN }
                    Chip("打开 JSBridge 测试页") {
                        WebViewActivity.start(context, "file:///android_asset/bridge_test.html", bridgeLevel)
                    }
                }
            }

            // ── 模拟升级（下次冷启动生效，不立即弹）──
            Section("模拟升级（下次启动生效）") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("可选升级") { UpdateController.setSimulateForNextLaunch(context, UpdateType.Optional) }
                    Chip("强制升级", danger = true) { UpdateController.setSimulateForNextLaunch(context, UpdateType.Force) }
                    Chip("无更新") { UpdateController.setSimulateForNextLaunch(context, UpdateType.None) }
                }
            }

            // ── Feature Flags ──
            val flags by DebugFlags.flags.collectAsState()
            Section("Feature Flags") {
                flags.forEach { (key, on) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(key, fontSize = 14.sp, color = TextMain)
                        Switch(checked = on, onCheckedChange = { DebugFlags.toggle(key) })
                    }
                }
            }

            // ── 日志 ──
            val logs by DebugLog.logs.collectAsState()
            Section("应用内日志 (${logs.size})") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("分享") {
                        val dump = DebugLog.dump().ifEmpty { "(空)" }
                        val file = File(context.cacheDir, "debug_log.txt").apply { writeText(dump) }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "分享日志").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    Chip("清空") { DebugLog.clear() }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (logs.isEmpty()) {
                        Text("(暂无日志，用 DebugLog.d/i/w/e 写入)", fontSize = 12.sp, color = TextSub)
                    } else {
                        logs.takeLast(200).forEach { e ->
                            Text(
                                "${e.level}/${e.tag}: ${e.msg}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (e.level == DebugLog.Level.E) Danger else TextSub,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun normalizeUrl(input: String): String =
    if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Accent)
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = TextSub)
        Text(value, fontSize = 13.sp, color = TextMain, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Chip(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 13.sp,
        color = if (danger) Danger else Accent,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(50))   // 先裁圆角，使按压 ripple 不超出控件边界
            .background((if (danger) Danger else Accent).copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
