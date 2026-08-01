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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.components.TopBarBackButton
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
private val IconBackground: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Primary.backgroundTertiary.current()
private val IconOnDark: Color
    @Composable @ReadOnlyComposable get() = IconColors.Default.onDark.current()

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
            title = "Notifications",
            description = "Used to send schedule reminders and status notifications for notes and recordings",
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
            title = "Microphone",
            description = "Used for voice notes, recording transcription, and related features",
            manifestPermissions = listOf(Manifest.permission.RECORD_AUDIO),
        )
    )
    add(
        AppPermission(
            key = "camera",
            iconRes = R.drawable.ic_camera,
            title = "Camera",
            description = "Used to take photos to insert into notes",
            manifestPermissions = listOf(Manifest.permission.CAMERA),
        )
    )
    add(
        AppPermission(
            key = "photos",
            iconRes = R.drawable.ic_image,
            title = "Photos and Media",
            description = "Used to select images from the gallery to insert into notes",
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding(),
    ) {
        // 顶栏：按 Figma 使用 36dp 扁平返回按钮与 22sp 标题。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 22.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TopBarBackButton(onClick = onBack)
            Text(
                "Permission Management",
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                color = TextTitle,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items, key = { it.permission.key }) { item ->
                PermissionRow(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun PermissionRow(item: PermissionUiItem, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = Card, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onClick,
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(IconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = item.permission.iconRes),
                    contentDescription = null,
                    tint = IconOnDark,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    item.permission.title,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextTitle,
                )
                Text(
                    item.permission.description,
                    fontSize = 12.sp,
                    color = TextSub,
                    lineHeight = 16.sp,
                )
            }
            PermissionStatusLabel(granted = item.status == PermissionStatus.GRANTED)
        }
    }
}

@Composable
private fun PermissionStatusLabel(granted: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (granted) "On" else "Off",
            fontSize = 12.sp,
            lineHeight = 20.sp,
            color = TextTitle,
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = IconColors.Default.default.current(),
            modifier = Modifier.size(16.dp),
        )
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
            AppPermission("notifications", R.drawable.ic_notification, "Notifications", "Used to send schedule reminders and status notifications for notes and recordings", listOf("x")),
            PermissionStatus.GRANTED,
        ),
        PermissionUiItem(
            AppPermission("microphone", R.drawable.ic_mic, "Microphone", "Used for voice notes, recording transcription, and related features", listOf("x")),
            PermissionStatus.GRANTED,
        ),
        PermissionUiItem(
            AppPermission("camera", R.drawable.ic_camera, "Camera", "Used to take photos to insert into notes", listOf("x")),
            PermissionStatus.DENIED,
        ),
        PermissionUiItem(
            AppPermission("photos", R.drawable.ic_image, "Photos and Media", "Used to select images from the gallery to insert into notes", listOf("x")),
            PermissionStatus.DENIED,
        ),
    )
    AppTheme {
        PermissionManagerContent(items = sample, onBack = {}, onItemClick = {})
    }
}
