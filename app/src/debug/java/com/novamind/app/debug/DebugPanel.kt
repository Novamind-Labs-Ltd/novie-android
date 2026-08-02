package com.novamind.app.debug
import com.novamind.app.common.log.DebugLog

import android.content.Intent
import android.os.Build
import android.os.StatFs
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.novamind.app.common.device.DeviceIdentity
import com.novamind.app.common.net.ApiConfig
import com.novamind.app.common.net.NetworkModule
import com.novamind.app.common.net.response.ApiResult
import com.novamind.app.common.net.response.apiCall
import com.novamind.app.common.onboarding.OnboardingStore
import com.novamind.app.common.update.UpdateController
import com.novamind.app.common.update.UpdateType
import com.novamind.app.ui.theme.AppFont
import com.novamind.app.ui.theme.AppFonts
import com.novamind.app.ui.theme.FontStore
import com.novamind.app.ui.theme.ThemeMode
import com.novamind.app.ui.theme.ThemeStore
import com.novamind.app.common.web.WebViewActivity
import com.novamind.app.common.web.bridge.SourceLevel
import com.novamind.app.util.SentryUtils
import com.novamind.app.debug.apitest.ApiTestActivity
import com.novamind.app.debug.apitest.ApiTarget
import com.novamind.app.debug.asknovie.AskNovieAnimActivity
import com.novamind.app.debug.files.FileBrowserActivity
import com.novamind.app.debug.imageupload.ImageUploadActivity
import com.novamind.app.debug.markdown.MarkdownPreviewActivity
import com.novamind.app.debug.notification.NotificationDebugger
import com.novamind.app.common.pdf.PdfViewerActivity
import com.novamind.app.debug.speech.SpeechToTextActivity
import com.novamind.app.common.google.GoogleCalendarAuthSource
import com.novamind.app.common.google.GoogleTokenProvider
import com.novamind.app.common.google.TokenOutcome
import com.novamind.app.data.calendar.CalendarEventCache
import com.novamind.app.feature.calendar.CalendarBindingStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
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

/** Debug 面板取 Hilt 依赖的入口（Composable 非注入宿主，经 EntryPoint 桥接）。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface DebugPanelEntryPoint {
    fun googleCalendarAuthSource(): GoogleCalendarAuthSource
    fun calendarBindingStore(): CalendarBindingStore
    fun calendarEventCache(): CalendarEventCache
}

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
    val entry = remember(context) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, DebugPanelEntryPoint::class.java)
    }
    val calAuthSource = remember(entry) { entry.googleCalendarAuthSource() }
    val calBinding = remember(entry) { entry.calendarBindingStore() }
    val calCache = remember(entry) { entry.calendarEventCache() }
    val scope = rememberCoroutineScope()

    val fingerprint = remember { DeviceIdentity.fingerprint(context) }

    var recCount by remember { mutableStateOf(-1) }
    var availMb by remember { mutableStateOf(-1L) }
    var totalMb by remember { mutableStateOf(-1L) }
    var refresh by remember { mutableStateOf(0) }

    // /api/v1.0/me 接口测试：结果以弹窗展示
    var meResult by remember { mutableStateOf<String?>(null) }
    var meLoading by remember { mutableStateOf(false) }

    // /api/v1.0/notes 列表接口测试：结果以弹窗展示
    var notesResult by remember { mutableStateOf<String?>(null) }
    var notesLoading by remember { mutableStateOf(false) }

    // Google 日历取消授权：结果以弹窗展示
    var revokeResult by remember { mutableStateOf<String?>(null) }
    var revoking by remember { mutableStateOf(false) }

    // 组件/能力测试
    var urlInput by remember { mutableStateOf("https://m.bing.com") }
    // JSBridge 测试页强制来源等级（null = 按域名白名单）
    var bridgeLevel by remember { mutableStateOf<SourceLevel?>(null) }
    // 录音实际存放在 filesDir/note_audio（见 AudioRecorder）
    val audioDir = remember { File(context.filesDir, "note_audio") }
    LaunchedEffect(refresh) {
        recCount = audioDir.listFiles()?.count { it.isFile } ?: 0
        // 内部存储分区可用/总空间（与录音、附件同一分区）
        val stat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        availMb = stat?.let { it.availableBytes / (1024 * 1024) } ?: -1L
        totalMb = stat?.let { it.totalBytes / (1024 * 1024) } ?: -1L
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
            Text("🛠 Debug Toolbox", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextMain)

            // ── 信息 ──
            Section("Info") {
                InfoRow("App", "${BuildConfig.APPLICATION_ID}")
                InfoRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                InfoRow("Build Type", BuildConfig.BUILD_TYPE + if (BuildConfig.DEBUG) " · DEBUG" else "")
                InfoRow("Build Time", BuildConfig.BUILD_TIME)
                InfoRow("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
                InfoRow("System", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                InfoRow("deviceId", fingerprint.androidId)
                InfoRow("UUID", fingerprint.installUuid)
                InfoRow("Resolution", fingerprint.screen)
                InfoRow("Timezone", fingerprint.timezone)
                InfoRow("Language", fingerprint.language)
            }

            // ── 域名 / 环境 ──
            val activeEnv by ApiConfig.envFlow.collectAsState()
            Section("Domain / Environment (applies after cold restart)") {
                InfoRow("Environment", activeEnv.label)
                InfoRow("api", ApiConfig.apiBaseUrl)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ApiConfig.Env.entries.forEach { env ->
                        val selected = env == activeEnv
                        Chip(if (selected) "✓ ${env.label}" else env.label) {
                            ApiConfig.select(env)
                        }
                    }
                }
                // 测试 /api/v1.0/me、/api/v1.0/notes：请求当前环境该接口，结果弹窗展示
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(if (meLoading) "Loading…" else "Test /api/v1.0/me") {
                        if (!meLoading) {
                            meLoading = true
                            scope.launch {
                                meResult = fetchMeRaw()
                                meLoading = false
                            }
                        }
                    }
                    Chip(if (notesLoading) "Loading…" else "Test GET /notes") {
                        if (!notesLoading) {
                            notesLoading = true
                            scope.launch {
                                notesResult = fetchNotesRaw()
                                notesLoading = false
                            }
                        }
                    }
                }
            }

            // /api/v1.0/me 结果弹窗
            meResult?.let { result ->
                AlertDialog(
                    onDismissRequest = { meResult = null },
                    confirmButton = { TextButton(onClick = { meResult = null }) { Text("Close") } },
                    title = { Text("GET /api/v1.0/me") },
                    text = {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(result, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    },
                )
            }

            // GET /api/v1.0/notes 结果弹窗
            notesResult?.let { result ->
                AlertDialog(
                    onDismissRequest = { notesResult = null },
                    confirmButton = { TextButton(onClick = { notesResult = null }) { Text("Close") } },
                    title = { Text("GET /api/v1.0/notes") },
                    text = {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(result, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    },
                )
            }

            // ── 主题（立即生效）──
            val activeTheme by ThemeStore.mode.collectAsState()
            Section("Theme (applies immediately)") {
                InfoRow("Current Theme", activeTheme.label)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m ->
                        Chip(if (m == activeTheme) "✓ ${m.label}" else m.label) {
                            ThemeStore.select(m)
                        }
                    }
                }
            }

            // ── 字体（立即生效）──
            val activeFont by FontStore.font.collectAsState()
            Section("Font (applies immediately)") {
                InfoRow("Current Font", activeFont.label)
                InfoRow(
                    "Google Sans Flex",
                    if (AppFonts.isGoogleSansFlexAvailable(context)) "Bundled" else "Not Bundled (falls back to system)",
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFont.entries.forEach { f ->
                        Chip(if (f == activeFont) "✓ ${f.label}" else f.label) {
                            FontStore.select(f)
                        }
                    }
                }
            }

            // ── 页面快速跳转 ──
            Section("Quick Navigation") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("home" to "Home", "create" to "Create", "library" to "Library",
                        "calendar" to "Calendar", "brand" to "Brand").forEach { (r, label) ->
                        Chip(label) { onNavigate(r) }
                    }
                }
            }

            // ── 接口测试 ──
            Section("API Testing") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("API Test Page (Python)") { ApiTestActivity.start(context, ApiTarget.ITEMS) }
                    Chip("API Test Page (Java)") { ApiTestActivity.start(context, ApiTarget.USERS) }
                    Chip("Image Upload Test") { ImageUploadActivity.start(context) }
                    Chip("Speech-to-Text Demo") { SpeechToTextActivity.start(context) }
                    Chip("Markdown Reader") { MarkdownPreviewActivity.start(context) }
                    Chip("PDF Preview") { PdfViewerActivity.start(context) }
                }
            }

            // ── UI / 动画 ──
            Section("UI / Motion") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Ask Novie Animation") { AskNovieAnimActivity.start(context) }
                }
            }

            // ── Google 日历授权 ──
            Section("Google Calendar Authorization") {
                InfoRow("Connected", if (calBinding.isConnected) "Yes" else "No")
                InfoRow("Account", calBinding.accountEmail ?: "-")
                InfoRow("In-memory token", if (GoogleTokenProvider.isAuthorized) "present" else "none")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 服务端吊销 + 清 GMS 缓存 + 清本地会话；下次连接需重新同意授权。
                    Chip(if (revoking) "Revoking…" else "Revoke Authorization", danger = true) {
                        if (!revoking) {
                            revoking = true
                            scope.launch {
                                revokeResult = revokeGoogleCalendar(calAuthSource, calBinding, calCache)
                                revoking = false
                                refresh++
                            }
                        }
                    }
                }
                Text(
                    "Revokes the Google Calendar/Tasks grant on Google's server and clears the local session. " +
                        "Reconnecting will prompt for consent again.",
                    fontSize = 12.sp, color = TextSub,
                )
            }

            // 取消授权结果弹窗
            revokeResult?.let { result ->
                AlertDialog(
                    onDismissRequest = { revokeResult = null },
                    confirmButton = { TextButton(onClick = { revokeResult = null }) { Text("Close") } },
                    title = { Text("Revoke Google Calendar") },
                    text = { Text(result, fontSize = 13.sp) },
                )
            }

            // ── 本地数据 ──
            Section("Local Data") {
                InfoRow("Recording Count", if (recCount < 0) "…" else "$recCount")
                // 内部存储空间（MB）
                InfoRow("Available Space", if (availMb < 0) "…" else "$availMb MB")
                InfoRow("Total Space", if (totalMb < 0) "…" else "$totalMb MB")
                // 录音存放目录绝对路径（应用私有内部存储，文件管理器不可见）
                InfoRow("Recording Directory", audioDir.absolutePath)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Open Recording Directory") {
                        audioDir.mkdirs()
                        FileBrowserActivity.start(context, audioDir.absolutePath)
                    }
                    Chip("Open filesDir") {
                        FileBrowserActivity.start(context, context.filesDir.absolutePath)
                    }
                    Chip("Clear Recordings", danger = true) {
                        scope.launch {
                            withContext(Dispatchers.IO) { audioDir.deleteRecursively() }
                            refresh++
                        }
                    }
                    Chip("Clear Images/Files", danger = true) {
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
            Section("Component / Capability Testing") {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Open WebView") {
                        urlInput.trim().takeIf { it.isNotEmpty() }
                            ?.let { WebViewActivity.start(context, normalizeUrl(it)) }
                    }
                    Chip("example.com") { WebViewActivity.start(context, "https://example.com") }
                    Chip("Bing") { WebViewActivity.start(context, "https://m.bing.com") }
                    Chip("Reset Onboarding") {
                        OnboardingStore.setCompleted(context, false)
                    }
                }
                // JSBridge：选来源等级 + 打开测试页（直观验证权限拦截 1003）
                Text(
                    "JSBridge Source Level: " + (bridgeLevel?.name ?: "By Domain Whitelist"),
                    fontSize = 12.sp, color = TextSub,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("By Domain") { bridgeLevel = null }
                    Chip("TRUSTED") { bridgeLevel = SourceLevel.TRUSTED }
                    Chip("PARTNER") { bridgeLevel = SourceLevel.PARTNER }
                    Chip("UNKNOWN") { bridgeLevel = SourceLevel.UNKNOWN }
                    Chip("Open JSBridge Test Page") {
                        WebViewActivity.start(context, "file:///android_asset/bridge_test.html", bridgeLevel)
                    }
                }
            }

            // ── 通知调试 ──
            Section("Notification Debugging (Screen Off/Lock Screen/Heads-up/Badge)") {
                // 每次进面板重建调试渠道，保证渠道参数改动生效
                LaunchedEffect(Unit) { NotificationDebugger.ensureChannels(context) }
                var badgeCount by remember { mutableStateOf(0) }
                var notifyHint by remember { mutableStateOf("") }
                InfoRow("Notification Permission", if (NotificationDebugger.areEnabled(context)) "Enabled" else "Disabled")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Notification Settings") { NotificationDebugger.openSettings(context) }
                    Chip("Heads-up Notification") {
                        NotificationDebugger.postHeadsUp(context)
                        notifyHint = "Sent: banner should appear when screen is on"
                    }
                    Chip("Lock Screen · Public") {
                        NotificationDebugger.postLockScreenDelayed(
                            context, androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC,
                        )
                        notifyHint = "Will send in 5s, please lock screen first: should display fully"
                    }
                    Chip("Lock Screen · Hide Content") {
                        NotificationDebugger.postLockScreenDelayed(
                            context, androidx.core.app.NotificationCompat.VISIBILITY_PRIVATE,
                        )
                        notifyHint = "Will send in 5s, please lock screen first: only app name shown, content hidden"
                    }
                    Chip("Lock Screen · Do Not Show") {
                        NotificationDebugger.postLockScreenDelayed(
                            context, androidx.core.app.NotificationCompat.VISIBILITY_SECRET,
                        )
                        notifyHint = "Will send in 5s, please lock screen first: should not appear on lock screen"
                    }
                    Chip("Screen Off Notification") {
                        NotificationDebugger.postScreenOffDelayed(context, fullScreen = false)
                        notifyHint = "Will send in 5s, please turn screen off first: observe whether it wakes the screen/appears on lock screen"
                    }
                    Chip("Screen Off · Full-Screen Intent") {
                        NotificationDebugger.postScreenOffDelayed(context, fullScreen = true)
                        notifyHint = "Will send in 5s, please turn screen off first: should wake screen and launch the page (API 34+ requires granting full-screen notification permission in system settings)"
                    }
                    Chip("Ongoing Notification") {
                        NotificationDebugger.postOngoing(context)
                        notifyHint = "Sent ongoing notification (visible on lock screen, with timer; Android 14+ users can still swipe it away)"
                    }
                    Chip("Screen Off · Ongoing Heads-up") {
                        NotificationDebugger.postOngoingHeadsUpDelayed(context)
                        notifyHint = "Will send in 5s, please turn screen off first: HIGH+ongoing, shows banner and stays when screen is on (whether it wakes the screen depends on the manufacturer)"
                    }
                    Chip("Cancel Ongoing") {
                        NotificationDebugger.cancelOngoing(context)
                        notifyHint = "Ongoing notification cancelled"
                    }
                    Chip("Badge +1") {
                        badgeCount++
                        NotificationDebugger.postBadge(context, badgeCount)
                        notifyHint = "Sent setNumber($badgeCount): check badge on home screen (depends on launcher support)"
                    }
                    Chip("Clear Notifications/Badge", danger = true) {
                        NotificationDebugger.clearAll(context)
                        badgeCount = 0
                        notifyHint = "Cleared all notifications from this tool"
                    }
                }
                if (notifyHint.isNotEmpty()) {
                    Text("✓ $notifyHint", fontSize = 12.sp, color = TextSub)
                }
            }

            // ── 模拟升级（下次冷启动生效，不立即弹）──
            Section("Simulate Update (applies on next launch)") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Optional Update") { UpdateController.setSimulateForNextLaunch(context, UpdateType.Optional) }
                    Chip("Force Update", danger = true) { UpdateController.setSimulateForNextLaunch(context, UpdateType.Force) }
                    Chip("No Update") { UpdateController.setSimulateForNextLaunch(context, UpdateType.None) }
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

            // ── Sentry 上报（按当前环境，dev 也会上传） ──
            Section("Sentry Reporting (environment=${SentryUtils.environment()})") {
                var lastAction by remember { mutableStateOf("") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Send Test Log") {
                        // Structured Logs：进入 Sentry「Logs」视图
                        SentryUtils.logInfo("Debug panel test log @ ${SentryUtils.environment()}")
                        lastAction = "Sent structured log (Logs view)"
                    }
                    Chip("Send Wide Event Log") {
                        // 携带自定义属性的宽事件日志，可在 Logs UI 按属性检索
                        SentryUtils.logEvent(
                            "Debug panel wide event",
                            attributes = mapOf(
                                "source" to "debug_panel",
                                "env" to SentryUtils.environment(),
                                "item_count" to 3,
                            ),
                        )
                        lastAction = "Sent wide event log (with attributes)"
                    }
                    Chip("Send Test Exception") {
                        SentryUtils.capture(
                            RuntimeException("Debug panel test exception"),
                            message = "Manual report from Debug Panel",
                        )
                        lastAction = "Sent test exception"
                    }
                    Chip("Add Breadcrumb") {
                        SentryUtils.breadcrumb("Debug breadcrumb: user action in debug panel", category = "debug")
                        lastAction = "Breadcrumb added"
                    }
                    Chip("Send Metric: count") {
                        SentryUtils.metricCount("debug_panel_click")
                        lastAction = "Sent metric count (Metrics view)"
                    }
                    Chip("Send Metric: distribution") {
                        SentryUtils.metricDistribution("debug_panel_value", 187.5)
                        lastAction = "Sent metric distribution (Metrics view)"
                    }
                    Chip("Send Metric: gauge") {
                        SentryUtils.metricGauge("debug_panel_gauge", 42.0)
                        lastAction = "Sent metric gauge (Metrics view)"
                    }
                    Chip("Set Scope Attribute") {
                        // 之后的所有日志都会带上该属性，便于在 Logs UI 过滤
                        SentryUtils.setLogAttribute("debug_session", "panel-${System.currentTimeMillis()}")
                        SentryUtils.logInfo("Log after scope attribute is set")
                        lastAction = "Scope attribute set and log sent"
                    }
                    Chip("Remove Scope Attribute") {
                        SentryUtils.removeLogAttribute("debug_session")
                        lastAction = "Removed scope attribute debug_session"
                    }
                }
                if (lastAction.isNotEmpty()) {
                    Text("✓ $lastAction (view in Sentry backend by environment=${SentryUtils.environment()})",
                        fontSize = 12.sp, color = TextSub)
                }
            }

            // ── 日志 ──
            val logs by DebugLog.logs.collectAsState()
            Section("In-App Logs (${logs.size})") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Share") {
                        val dump = DebugLog.dump().ifEmpty { "(empty)" }
                        val file = File(context.cacheDir, "debug_log.txt").apply { writeText(dump) }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Share Log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    Chip("Clear") { DebugLog.clear() }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (logs.isEmpty()) {
                        Text("(No logs yet, use AppLog.d/i/w/e to write)", fontSize = 12.sp, color = TextSub)
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

/**
 * 取消 Google 日历授权（debug 工具）：尽力做「服务端吊销 + 清 GMS 缓存 + 清本地会话」。
 *
 * token 优先取内存态；内存没有（如冷启动未静默取过）则用绑定邮箱静默取一次，
 * 以便真正打 Google 吊销接口。取不到 token 则只清本地（服务端授权可能仍在）。
 */
private suspend fun revokeGoogleCalendar(
    authSource: GoogleCalendarAuthSource,
    binding: CalendarBindingStore,
    cache: CalendarEventCache,
): String {
    val email = binding.accountEmail
    var token = GoogleTokenProvider.accessToken
    if (token == null && email != null) {
        token = (authSource.fetchToken(email) as? TokenOutcome.Success)?.token
    }
    var serverRevoked = false
    token?.let {
        authSource.clearToken(it)   // 清设备端 GMS token 缓存
        authSource.revoke(it)       // 打 Google 吊销端点（best-effort，失败不抛）
        serverRevoked = true
    }
    // 清本地会话：内存 token / 绑定 / 事件缓存
    GoogleTokenProvider.clear()
    binding.clear()
    cache.clear()
    return buildString {
        appendLine(if (serverRevoked) "✅ Server grant revoked" else "⚠ No token available — cleared local session only")
        appendLine("Account: ${email ?: "-"}")
        appendLine("Cleared: in-memory token · binding · event cache")
        append(
            if (serverRevoked) "Reconnecting will require consent again."
            else "Server grant may still exist; revoke it in Google Account settings if needed.",
        )
    }
}

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

/** 请求当前环境的 GET /api/v1.0/me（Authorization 由拦截器自动附加），返回可读文本供弹窗展示。 */
private suspend fun fetchMeRaw(): String = withContext(Dispatchers.IO) {
    val url = ApiConfig.apiBaseUrl + "api/v1.0/me"
    runCatching {
        val resp = NetworkModule.apiService.get(url)
        val raw = (if (resp.isSuccessful) resp.body()?.string() else resp.errorBody()?.string()).orEmpty()
        "URL: $url\nHTTP ${resp.code()}\n\n${prettyJson(raw)}"
    }.getOrElse { "URL: $url\n\nRequest failed: ${it.message}" }
}

/**
 * 请求当前环境的 GET /api/v1.0/notes（活跃列表，limit=20；Authorization 由拦截器附加）。
 * 走类型化 [NetworkModule.notesApi] + 统一 [apiCall]，按 [ApiResult] 三态格式化供弹窗展示。
 */
private suspend fun fetchNotesRaw(): String = withContext(Dispatchers.IO) {
    val header = "URL: ${ApiConfig.apiBaseUrl}api/v1.0/notes?limit=20\n\n"
    when (val result = apiCall { NetworkModule.notesApi.list(limit = 20) }) {
        is ApiResult.Success -> {
            val page = result.data
            val items = page?.items.orEmpty()
            buildString {
                append(header)
                append("✅ Success · ${items.size} items · nextCursor=${page?.nextCursor ?: "null"}\n\n")
                if (items.isEmpty()) {
                    append("(no notes)")
                } else {
                    items.forEachIndexed { i, it ->
                        append("${i + 1}. [${it.id.take(8)}] ${it.title ?: "(untitled)"}\n")
                        append("   folderId=${it.folderId ?: "-"}  trashed=${it.trashed}\n")
                    }
                }
            }
        }
        is ApiResult.BizError ->
            "${header}⚠ BizError\ncode=${result.code}  http=${result.httpStatus}\nmessage=${result.message}\ntraceId=${result.traceId ?: "-"}"
        is ApiResult.NetworkError ->
            "${header}⚠ NetworkError  http=${result.httpStatus ?: "-"}\n${result.message ?: result.cause?.message ?: "unknown"}"
    }
}

/** 尽力把 JSON 缩进美化；非 JSON 或解析失败则原样返回。 */
private fun prettyJson(s: String): String = runCatching {
    val t = s.trimStart()
    when {
        t.startsWith("{") -> org.json.JSONObject(s).toString(2)
        t.startsWith("[") -> org.json.JSONArray(s).toString(2)
        else -> s
    }
}.getOrDefault(s)
