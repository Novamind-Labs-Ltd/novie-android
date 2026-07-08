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
import com.novamind.app.debug.files.FileBrowserActivity
import com.novamind.app.debug.imageupload.ImageUploadActivity
import com.novamind.app.debug.markdown.MarkdownPreviewActivity
import com.novamind.app.debug.notification.NotificationDebugger
import com.novamind.app.common.pdf.PdfViewerActivity
import com.novamind.app.debug.speech.SpeechToTextActivity
import com.novamind.app.data.NoteRepository
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
    fun noteRepository(): NoteRepository
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
    val noteRepository = remember(context) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, DebugPanelEntryPoint::class.java)
            .noteRepository()
    }
    val scope = rememberCoroutineScope()

    val fingerprint = remember { DeviceIdentity.fingerprint(context) }

    var noteCount by remember { mutableStateOf(-1) }
    var recCount by remember { mutableStateOf(-1) }
    var availMb by remember { mutableStateOf(-1L) }
    var totalMb by remember { mutableStateOf(-1L) }
    var refresh by remember { mutableStateOf(0) }

    // /api/v1.0/me 接口测试：结果以弹窗展示
    var meResult by remember { mutableStateOf<String?>(null) }
    var meLoading by remember { mutableStateOf(false) }

    // 组件/能力测试
    var urlInput by remember { mutableStateOf("https://m.bing.com") }
    // JSBridge 测试页强制来源等级（null = 按域名白名单）
    var bridgeLevel by remember { mutableStateOf<SourceLevel?>(null) }
    // 录音实际存放在 filesDir/note_audio（见 AudioRecorder）
    val audioDir = remember { File(context.filesDir, "note_audio") }
    LaunchedEffect(refresh) {
        noteCount = runCatching { noteRepository.count() }
            .onFailure { if (it is CancellationException) throw it }
            .getOrDefault(-1)
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
                InfoRow("deviceId", fingerprint.androidId)
                InfoRow("UUID", fingerprint.installUuid)
                InfoRow("分辨率", fingerprint.screen)
                InfoRow("时区", fingerprint.timezone)
                InfoRow("语言", fingerprint.language)
            }

            // ── 域名 / 环境 ──
            val activeEnv by ApiConfig.envFlow.collectAsState()
            Section("域名 / 环境（切换后冷启动生效）") {
                InfoRow("环境", activeEnv.label)
                InfoRow("api", ApiConfig.apiBaseUrl)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ApiConfig.Env.entries.forEach { env ->
                        val selected = env == activeEnv
                        Chip(if (selected) "✓ ${env.label}" else env.label) {
                            ApiConfig.select(env)
                        }
                    }
                }
                // 测试 /api/v1.0/me：请求当前环境该接口，结果弹窗展示
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(if (meLoading) "请求中…" else "测试 /api/v1.0/me") {
                        if (!meLoading) {
                            meLoading = true
                            scope.launch {
                                meResult = fetchMeRaw()
                                meLoading = false
                            }
                        }
                    }
                }
            }

            // /api/v1.0/me 结果弹窗
            meResult?.let { result ->
                AlertDialog(
                    onDismissRequest = { meResult = null },
                    confirmButton = { TextButton(onClick = { meResult = null }) { Text("关闭") } },
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

            // ── 主题（立即生效）──
            val activeTheme by ThemeStore.mode.collectAsState()
            Section("主题（立即生效）") {
                InfoRow("当前主题", activeTheme.label)
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
            Section("字体（立即生效）") {
                InfoRow("当前字体", activeFont.label)
                InfoRow(
                    "Google Sans Flex",
                    if (AppFonts.isGoogleSansFlexAvailable(context)) "已内置" else "未内置(回退系统)",
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
            Section("页面快速跳转") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("home" to "Home", "create" to "Create", "library" to "Library",
                        "calendar" to "Calendar", "brand" to "Brand").forEach { (r, label) ->
                        Chip(label) { onNavigate(r) }
                    }
                }
            }

            // ── 接口测试 ──
            Section("接口测试") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("接口测试页 Python") { ApiTestActivity.start(context, ApiTarget.ITEMS) }
                    Chip("接口测试页 Java") { ApiTestActivity.start(context, ApiTarget.USERS) }
                    Chip("图片上传测试") { ImageUploadActivity.start(context) }
                    Chip("语音转文字 Demo") { SpeechToTextActivity.start(context) }
                    Chip("Markdown 阅读器") { MarkdownPreviewActivity.start(context) }
                    Chip("PDF 预览") { PdfViewerActivity.start(context) }
                }
            }

            // ── 本地数据 ──
            Section("本地数据") {
                InfoRow("笔记数", if (noteCount < 0) "…" else "$noteCount")
                InfoRow("录音数", if (recCount < 0) "…" else "$recCount")
                // 内部存储空间（MB）
                InfoRow("可用空间", if (availMb < 0) "…" else "$availMb MB")
                InfoRow("总空间", if (totalMb < 0) "…" else "$totalMb MB")
                // 录音存放目录绝对路径（应用私有内部存储，文件管理器不可见）
                InfoRow("录音目录", audioDir.absolutePath)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("打开录音目录") {
                        audioDir.mkdirs()
                        FileBrowserActivity.start(context, audioDir.absolutePath)
                    }
                    Chip("打开 filesDir") {
                        FileBrowserActivity.start(context, context.filesDir.absolutePath)
                    }
                    Chip("清空笔记", danger = true) {
                        scope.launch { noteRepository.clearAll(); refresh++ }
                    }
                    Chip("清空录音", danger = true) {
                        scope.launch {
                            withContext(Dispatchers.IO) { audioDir.deleteRecursively() }
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

            // ── 通知调试 ──
            Section("通知调试（熄屏/锁屏/悬浮/角标）") {
                // 每次进面板重建调试渠道，保证渠道参数改动生效
                LaunchedEffect(Unit) { NotificationDebugger.ensureChannels(context) }
                var badgeCount by remember { mutableStateOf(0) }
                var notifyHint by remember { mutableStateOf("") }
                InfoRow("通知权限", if (NotificationDebugger.areEnabled(context)) "已开启" else "已关闭")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("通知设置") { NotificationDebugger.openSettings(context) }
                    Chip("悬浮通知") {
                        NotificationDebugger.postHeadsUp(context)
                        notifyHint = "已发送：亮屏时应弹出横幅"
                    }
                    Chip("锁屏·公开") {
                        NotificationDebugger.postLockScreenDelayed(
                            context, androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC,
                        )
                        notifyHint = "5s 后发送，请先锁屏：应完整显示"
                    }
                    Chip("锁屏·隐藏内容") {
                        NotificationDebugger.postLockScreenDelayed(
                            context, androidx.core.app.NotificationCompat.VISIBILITY_PRIVATE,
                        )
                        notifyHint = "5s 后发送，请先锁屏：只显示应用名、隐藏内容"
                    }
                    Chip("锁屏·不显示") {
                        NotificationDebugger.postLockScreenDelayed(
                            context, androidx.core.app.NotificationCompat.VISIBILITY_SECRET,
                        )
                        notifyHint = "5s 后发送，请先锁屏：锁屏上不应出现"
                    }
                    Chip("熄屏通知") {
                        NotificationDebugger.postScreenOffDelayed(context, fullScreen = false)
                        notifyHint = "5s 后发送，请先熄屏：观察是否点亮/出现在锁屏"
                    }
                    Chip("熄屏·全屏意图") {
                        NotificationDebugger.postScreenOffDelayed(context, fullScreen = true)
                        notifyHint = "5s 后发送，请先熄屏：应点亮并拉起页面（API 34+ 需在系统设置授予全屏通知权限）"
                    }
                    Chip("常驻通知") {
                        NotificationDebugger.postOngoing(context)
                        notifyHint = "已发送 ongoing 常驻通知（锁屏公开可见，带计时器；Android 14+ 用户仍可滑除）"
                    }
                    Chip("熄屏·常驻悬浮") {
                        NotificationDebugger.postOngoingHeadsUpDelayed(context)
                        notifyHint = "5s 后发送，请先熄屏：HIGH+ongoing，亮屏弹横幅并常驻（能否点亮屏幕看厂商）"
                    }
                    Chip("取消常驻") {
                        NotificationDebugger.cancelOngoing(context)
                        notifyHint = "已取消常驻通知"
                    }
                    Chip("角标 +1") {
                        badgeCount++
                        NotificationDebugger.postBadge(context, badgeCount)
                        notifyHint = "已发送 setNumber($badgeCount)：回桌面看角标（依赖启动器支持）"
                    }
                    Chip("清除通知/角标", danger = true) {
                        NotificationDebugger.clearAll(context)
                        badgeCount = 0
                        notifyHint = "已清除本工具全部通知"
                    }
                }
                if (notifyHint.isNotEmpty()) {
                    Text("✓ $notifyHint", fontSize = 12.sp, color = TextSub)
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

            // ── Sentry 上报（按当前环境，dev 也会上传） ──
            Section("Sentry 上报（environment=${SentryUtils.environment()}）") {
                var lastAction by remember { mutableStateOf("") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("上报测试日志") {
                        // Structured Logs：进入 Sentry「Logs」视图
                        SentryUtils.logInfo("Debug panel test log @ ${SentryUtils.environment()}")
                        lastAction = "已发送结构化日志（Logs 视图）"
                    }
                    Chip("上报宽事件日志") {
                        // 携带自定义属性的宽事件日志，可在 Logs UI 按属性检索
                        SentryUtils.logEvent(
                            "Debug panel wide event",
                            attributes = mapOf(
                                "source" to "debug_panel",
                                "env" to SentryUtils.environment(),
                                "item_count" to 3,
                            ),
                        )
                        lastAction = "已发送宽事件日志（含属性）"
                    }
                    Chip("上报测试异常") {
                        SentryUtils.capture(
                            RuntimeException("Debug panel test exception"),
                            message = "来自 Debug 面板的手动上报",
                        )
                        lastAction = "已发送测试异常"
                    }
                    Chip("添加面包屑") {
                        SentryUtils.breadcrumb("Debug 面包屑：用户在调试面板操作", category = "debug")
                        lastAction = "已添加面包屑"
                    }
                    Chip("上报指标 count") {
                        SentryUtils.metricCount("debug_panel_click")
                        lastAction = "已上报指标 count（Metrics 视图）"
                    }
                    Chip("上报指标 distribution") {
                        SentryUtils.metricDistribution("debug_panel_value", 187.5)
                        lastAction = "已上报指标 distribution（Metrics 视图）"
                    }
                    Chip("上报指标 gauge") {
                        SentryUtils.metricGauge("debug_panel_gauge", 42.0)
                        lastAction = "已上报指标 gauge（Metrics 视图）"
                    }
                    Chip("设置作用域属性") {
                        // 之后的所有日志都会带上该属性，便于在 Logs UI 过滤
                        SentryUtils.setLogAttribute("debug_session", "panel-${System.currentTimeMillis()}")
                        SentryUtils.logInfo("作用域属性已设置后的日志")
                        lastAction = "已设置作用域属性并发送一条日志"
                    }
                    Chip("移除作用域属性") {
                        SentryUtils.removeLogAttribute("debug_session")
                        lastAction = "已移除作用域属性 debug_session"
                    }
                }
                if (lastAction.isNotEmpty()) {
                    Text("✓ $lastAction（Sentry 后台按 environment=${SentryUtils.environment()} 查看）",
                        fontSize = 12.sp, color = TextSub)
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
                        Text("(暂无日志，用 AppLog.d/i/w/e 写入)", fontSize = 12.sp, color = TextSub)
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

/** 请求当前环境的 GET /api/v1.0/me（Authorization 由拦截器自动附加），返回可读文本供弹窗展示。 */
private suspend fun fetchMeRaw(): String = withContext(Dispatchers.IO) {
    val url = ApiConfig.apiBaseUrl + "api/v1.0/me"
    runCatching {
        val resp = NetworkModule.apiService.get(url)
        val raw = (if (resp.isSuccessful) resp.body()?.string() else resp.errorBody()?.string()).orEmpty()
        "URL: $url\nHTTP ${resp.code()}\n\n${prettyJson(raw)}"
    }.getOrElse { "URL: $url\n\n请求失败: ${it.message}" }
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
