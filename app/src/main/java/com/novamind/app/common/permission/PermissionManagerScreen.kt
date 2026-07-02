package com.novamind.app.common.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.ReadOnlyComposable
import com.novamind.app.R
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.components.BackButton
import com.novamind.app.ui.theme.AppTheme

/* ---------------------------------------------------------------------------
 * 配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析（不使用硬编码颜色）
 * ------------------------------------------------------------------------- */
private val BgPage: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
private val Card: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val TextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val TextSub: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
private val Accent: Color
    @Composable @ReadOnlyComposable get() = IconColors.Brand.default.current()
private val AccentSoft: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Scenario.fern.current()
private val Divider: Color
    @Composable @ReadOnlyComposable get() = BorderColors.Default.default.current()
private val PillOffBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.inset.current()
private val PillOffText: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.tertiary.current()

/* ---------------------------------------------------------------------------
 * 数据模型
 * ------------------------------------------------------------------------- */

/** 单条权限的授权状态。 */
enum class PermissionStatus { GRANTED, DENIED }

/**
 * 一个面向用户的权限分组。一个分组可能对应多个底层系统权限串
 * （如「照片和媒体」在不同 Android 版本上对应不同权限），全部授予才算开启。
 *
 * @param key          稳定标识，用于 LazyColumn key
 * @param iconRes      图标资源
 * @param title        权限名称（用户视角）
 * @param description  用途说明：解释「为什么要这个权限」
 * @param manifestPermissions 当前运行版本需检测/申请的系统权限串；为空表示该项仅能在系统设置中管理
 */
data class AppPermission(
    val key: String,
    val iconRes: Int,
    val title: String,
    val description: String,
    val manifestPermissions: List<String>,
)

/** UI 渲染用：权限 + 实时状态。 */
data class PermissionUiItem(
    val permission: AppPermission,
    val status: PermissionStatus,
)

/* ---------------------------------------------------------------------------
 * 权限清单：仅列出本 App 真实使用到的权限
 * ------------------------------------------------------------------------- */
private fun appPermissions(): List<AppPermission> = buildList {
    add(
        AppPermission(
            key = "notifications",
            iconRes = R.drawable.ic_notification,
            title = "通知",
            description = "用于发送日程提醒、笔记与录音相关的状态通知",
            manifestPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyList() // 13 以下无运行时通知权限，由系统设置统一管理
            },
        )
    )
    add(
        AppPermission(
            key = "microphone",
            iconRes = R.drawable.ic_mic,
            title = "麦克风",
            description = "用于语音速记、录音转写等功能",
            manifestPermissions = listOf(Manifest.permission.RECORD_AUDIO),
        )
    )
    add(
        AppPermission(
            key = "camera",
            iconRes = R.drawable.ic_camera,
            title = "相机",
            description = "用于在笔记中拍照插图",
            manifestPermissions = listOf(Manifest.permission.CAMERA),
        )
    )
    add(
        AppPermission(
            key = "photos",
            iconRes = R.drawable.ic_image,
            title = "照片和媒体",
            description = "用于从相册选择图片插入笔记",
            manifestPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                @Suppress("DEPRECATION")
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            },
        )
    )
}

/** 检测某权限分组的实时状态。空权限串（如低版本通知）按已授予处理。 */
private fun AppPermission.currentStatus(context: Context): PermissionStatus {
    if (manifestPermissions.isEmpty()) return PermissionStatus.GRANTED
    val allGranted = manifestPermissions.all {
        ContextCompat.checkSelfPermission(context, it) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    return if (allGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED
}

/** 跳转到本应用的系统「应用信息 / 权限」页。 */
private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

/* ---------------------------------------------------------------------------
 * 有状态外壳：负责系统交互（实时检测 / 申请 / 跳设置 / 返回前台刷新）
 * ------------------------------------------------------------------------- */

/**
 * 系统权限管理页。从个人中心抽屉进入，点左上角或系统返回关闭。
 *
 * 行为符合大厂规范：
 * - 进入与「从系统设置返回前台」时实时刷新各权限状态（ON_RESUME 监听）。
 * - 未授权且可申请 → 直接拉起系统授权弹窗；用户勾选「不再询问」后被永久拒绝 →
 *   再次点击自动跳转系统设置页。
 * - 已授权 → 点击跳系统设置以便用户主动关闭（系统不允许 App 直接撤销权限）。
 */
@Composable
fun PermissionManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissions = remember { appPermissions() }
    // 实时状态：随生命周期 ON_RESUME 重新读取，保证从系统设置返回后即时更新
    var statuses by remember {
        mutableStateOf(permissions.associate { it.key to it.currentStatus(context) })
    }
    fun refresh() {
        statuses = permissions.associate { it.key to it.currentStatus(context) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 记录每个权限是否已经发起过运行时申请——用于区分「首次拒绝」与「永久拒绝」
    val requested = remember { mutableStateOf(setOf<String>()) }
    var pendingKey by remember { mutableStateOf<String?>(null) }

    val requestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val key = pendingKey
        pendingKey = null
        refresh()
        // 申请后仍未授予 → 多为永久拒绝，引导用户去系统设置
        val granted = result.values.isNotEmpty() && result.values.all { it }
        if (!granted && key != null) {
            requested.value = requested.value + key
        }
    }

    val items = permissions.map {
        PermissionUiItem(it, statuses[it.key] ?: PermissionStatus.DENIED)
    }

    PermissionManagerContent(
        items = items,
        onBack = onBack,
        onItemClick = { item ->
            val p = item.permission
            when {
                // 已授权：系统不允许 App 直接撤销，跳系统设置由用户操作
                item.status == PermissionStatus.GRANTED -> openAppSettings(context)
                // 仅能在系统设置管理（如低版本通知）
                p.manifestPermissions.isEmpty() -> openAppSettings(context)
                // 已申请过且仍被拒 → 视为永久拒绝，跳系统设置
                requested.value.contains(p.key) -> openAppSettings(context)
                // 首次申请 → 拉起系统授权弹窗
                else -> {
                    pendingKey = p.key
                    requestLauncher.launch(p.manifestPermissions.toTypedArray())
                }
            }
        },
        onOpenSystemSettings = { openAppSettings(context) },
    )
}

/* ---------------------------------------------------------------------------
 * 无状态 UI：纯渲染，可预览、可测试
 * ------------------------------------------------------------------------- */

@Composable
fun PermissionManagerContent(
    items: List<PermissionUiItem>,
    onBack: () -> Unit,
    onItemClick: (PermissionUiItem) -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grantedCount = items.count { it.status == PermissionStatus.GRANTED }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding(),
    ) {
        // 顶栏：返回 | 标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BackButton(onClick = onBack, background = Card, tint = TextTitle, contentDescription = "返回")
            Text(
                "权限管理",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextTitle,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 概览卡：已开启数量 + 说明
            item {
                SummaryCard(grantedCount = grantedCount, total = items.size)
                Spacer(Modifier.height(8.dp))
                SectionLabel("应用权限")
            }

            items(items, key = { it.permission.key }) { item ->
                PermissionRow(item = item, onClick = { onItemClick(item) })
            }

            // 底部统一入口 + 说明
            item {
                Spacer(Modifier.height(6.dp))
                SystemSettingsEntry(onClick = onOpenSystemSettings)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "权限由系统统一管理。出于安全考虑，应用无法直接开启或关闭权限，" +
                        "你可随时在系统设置中修改。",
                    fontSize = 12.sp,
                    color = TextSub,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(grantedCount: Int, total: Int) {
    Surface(shape = RoundedCornerShape(16.dp), color = Card, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "已开启 $grantedCount / $total 项权限",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextTitle,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "管理本应用使用的系统权限",
                    fontSize = 12.sp,
                    color = TextSub,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = TextSub,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 6.dp),
    )
}

@Composable
private fun PermissionRow(item: PermissionUiItem, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = Card, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onClick,
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = item.permission.iconRes),
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            // 标题 + 说明
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    item.permission.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextTitle,
                )
                Text(
                    item.permission.description,
                    fontSize = 12.sp,
                    color = TextSub,
                    lineHeight = 17.sp,
                )
            }
            // 状态徽章
            StatusPill(granted = item.status == PermissionStatus.GRANTED)
        }
    }
}

@Composable
private fun StatusPill(granted: Boolean) {
    val bg = if (granted) AccentSoft else PillOffBg
    val fg = if (granted) Accent else PillOffText
    val label = if (granted) "已开启" else "去开启"
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}

@Composable
private fun SystemSettingsEntry(onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = Card, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onClick,
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "在系统设置中管理",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextTitle,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = TextSub,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/* ---------------------------------------------------------------------------
 * 预览
 * ------------------------------------------------------------------------- */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PermissionManagerPreview() {
    val sample = listOf(
        PermissionUiItem(
            AppPermission("notifications", R.drawable.ic_notification, "通知", "用于发送日程提醒、笔记与录音相关的状态通知", listOf("x")),
            PermissionStatus.GRANTED,
        ),
        PermissionUiItem(
            AppPermission("microphone", R.drawable.ic_mic, "麦克风", "用于语音速记、录音转写等功能", listOf("x")),
            PermissionStatus.GRANTED,
        ),
        PermissionUiItem(
            AppPermission("camera", R.drawable.ic_camera, "相机", "用于在笔记中拍照插图", listOf("x")),
            PermissionStatus.DENIED,
        ),
        PermissionUiItem(
            AppPermission("photos", R.drawable.ic_image, "照片和媒体", "用于从相册选择图片插入笔记", listOf("x")),
            PermissionStatus.DENIED,
        ),
    )
    AppTheme {
        PermissionManagerContent(items = sample, onBack = {}, onItemClick = {}, onOpenSystemSettings = {})
    }
}
