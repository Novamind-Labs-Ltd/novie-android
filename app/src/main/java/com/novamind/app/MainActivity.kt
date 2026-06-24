package com.novamind.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.novamind.app.common.deeplink.DeepLinks
import com.novamind.app.common.onboarding.OnboardingScreen
import com.novamind.app.common.onboarding.OnboardingStore
import com.novamind.app.common.update.UpdateController
import com.novamind.app.common.update.UpdateDialog
import com.novamind.app.feature.calendar.CalendarRoute
import com.novamind.app.feature.create.CreateRoute
import com.novamind.app.feature.home.HomeRoute
import com.novamind.app.feature.library.LibraryRoute
import com.novamind.app.feature.asknovie.AskNovieScreen
import com.novamind.app.feature.auth.AuthViewModel
import com.novamind.app.feature.auth.BiometricLockScreen
import com.novamind.app.feature.auth.LoginRoute
import com.novamind.app.ui.components.AppBottomNavBar
import com.novamind.app.ui.components.BottomNavDestination
import com.novamind.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.messaging.FirebaseMessaging
import com.novamind.app.common.log.DebugLog

// 导航顺序，用于判断滑动方向
private val navOrder = listOf(
    BottomNavDestination.Brand.route,
    BottomNavDestination.Home.route,
    BottomNavDestination.Create.route,
    BottomNavDestination.Library.route,
    BottomNavDestination.Calendar.route,
)

class MainActivity : FragmentActivity() {

    // App Links 进入时的目标 route：由 onCreate / onNewIntent 写入，Compose 侧 LaunchedEffect 消费后清空
    private var deepLinkRoute by mutableStateOf<String?>(null)

    // Android 13+ 通知权限申请器；授予与否都不阻塞主流程
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            DebugLog.i("Fcm", "POST_NOTIFICATIONS granted=$granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 冷启动若由 App Link 拉起，解析目标页（Compose 侧消费）
        deepLinkRoute = DeepLinks.resolve(intent)
        askNotificationPermission()
        logFcmToken()
        // Block-editor 光标方案：adjustNothing —— 键盘弹出窗口不重排，内容/光标布局不动，
        // 仅由编辑器在「光标被键盘遮住」时自行滚动。
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        setContent {
            AppTheme {
                var currentRoute by rememberSaveable {
                    mutableStateOf(BottomNavDestination.Home.route)
                }
                var editingNoteId by rememberSaveable { mutableStateOf<String?>(null) }
                // 图片预览等全屏页打开时隐藏底部导航栏
                var hideBottomNav by rememberSaveable { mutableStateOf(false) }
                // Ask Novie 聊天页（点底部导航最左品牌按钮打开）
                var showAskNovie by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = showAskNovie) { showAskNovie = false }
                // 首启引导页
                val appContext = LocalContext.current
                var showOnboarding by rememberSaveable {
                    mutableStateOf(!OnboardingStore.isCompleted(appContext))
                }

                // 认证状态（Auth0）：未登录时用登录页门控整个应用
                val authViewModel: AuthViewModel = viewModel()
                val authState by authViewModel.uiState.collectAsStateWithLifecycle()

                // 进程级前后台监听：后台超时后回前台触发生物识别重新上锁
                DisposableEffect(authViewModel) {
                    val owner = ProcessLifecycleOwner.get()
                    val observer = object : DefaultLifecycleObserver {
                        override fun onStop(owner: LifecycleOwner) = authViewModel.onAppBackgrounded()
                        override fun onStart(owner: LifecycleOwner) = authViewModel.onAppForegrounded()
                    }
                    owner.lifecycle.addObserver(observer)
                    onDispose { owner.lifecycle.removeObserver(observer) }
                }

                // 冷启动检查升级（Mock 策略；可在 Debug 工具箱模拟）
                LaunchedEffect(Unit) { UpdateController.checkOnStartup(appContext, BuildConfig.VERSION_CODE) }

                // App Link 进入：切到目标页并退出覆盖层，消费后清空避免重复触发
                LaunchedEffect(deepLinkRoute) {
                    deepLinkRoute?.let { target ->
                        currentRoute = target
                        editingNoteId = null
                        showAskNovie = false
                        deepLinkRoute = null
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = currentRoute,
                        modifier = Modifier.fillMaxSize(),   // 填满屏幕，给子页面有界高度（CreateScreen 的 weight 依赖此）
                        transitionSpec = {
                            val fromIndex = navOrder.indexOf(initialState)
                            val toIndex = navOrder.indexOf(targetState)
                            // Create/编辑页视作从右侧推入，返回时向右滑出
                            val forward = toIndex >= fromIndex
                            if (forward) {
                                (slideInHorizontally { it } + fadeIn(initialAlpha = 0.3f))
                                    .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
                            } else {
                                (slideInHorizontally { -it / 3 } + fadeIn(initialAlpha = 0.3f))
                                    .togetherWith(slideOutHorizontally { it } + fadeOut())
                            }
                        },
                        label = "page_transition",
                    ) { route ->
                        when (route) {
                            BottomNavDestination.Home.route -> HomeRoute(
                                onNoteClick = { noteId ->
                                    editingNoteId = noteId
                                    currentRoute = BottomNavDestination.Create.route
                                },
                                onFullscreenChange = { hideBottomNav = it },
                                onLogout = { authViewModel.logout(this@MainActivity) },
                                onSwitchToLogin = { authViewModel.exitGuest() },
                                userName = authState.userName,
                                userEmail = authState.userEmail,
                                isGuest = authState.isGuest,
                                biometricAvailable = authState.biometricAvailable,
                                biometricEnabled = authState.biometricEnabled,
                                onToggleBiometric = { authViewModel.setBiometricEnabled(it) },
                            )
                            BottomNavDestination.Create.route -> CreateRoute(
                                noteId = editingNoteId,
                                onBack = {
                                    editingNoteId = null
                                    currentRoute = BottomNavDestination.Home.route
                                },
                                onFullscreenChange = { hideBottomNav = it },
                            )
                            BottomNavDestination.Library.route -> LibraryRoute(
                                onCreateNote = {
                                    editingNoteId = null
                                    currentRoute = BottomNavDestination.Create.route
                                },
                            )
                            BottomNavDestination.Calendar.route -> CalendarRoute()
                        }
                    }

                    // 全屏页（如图片预览）打开、或进入 Create 编辑页时滑出隐藏底部导航栏
                    AnimatedVisibility(
                        visible = !hideBottomNav && currentRoute != BottomNavDestination.Create.route,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        AppBottomNavBar(
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                // 最左品牌按钮 → 打开 Ask Novie，不切换底部 tab
                                if (route == BottomNavDestination.Brand.route) {
                                    showAskNovie = true
                                    return@AppBottomNavBar
                                }
                                // 已在当前页（如编辑中点 Create）→ 保持不变，不重置不跳转
                                if (route == currentRoute) return@AppBottomNavBar
                                if (route == BottomNavDestination.Create.route) editingNoteId = null
                                currentRoute = route
                            },
                        )
                    }

                    // Ask Novie 聊天页（全屏覆盖，自带返回；带左右滑动转场）
                    AnimatedVisibility(
                        visible = showAskNovie,
                        enter = slideInHorizontally { it } + fadeIn(),
                        exit = slideOutHorizontally { it } + fadeOut(),
                    ) {
                        AskNovieScreen(
                            onBack = { showAskNovie = false },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // Debug 工具箱：仅 debug 变体有实现（摇一摇打开），release 为空实现。
                    // 调试工具及其依赖只存在于 src/debug，不会编入 release 包。
                    DebugOverlay(onNavigate = { route ->
                        editingNoteId = null
                        currentRoute = route
                    })

                    // 升级弹窗（可选可关闭；强制不可关闭）
                    val update by UpdateController.state.collectAsState()
                    update?.let { info ->
                        UpdateDialog(
                            info = info,
                            onUpdate = {
                                runCatching {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
                                }
                            },
                            onLater = { UpdateController.dismiss() },
                            onExit = { finish() },
                        )
                    }

                    // 首启引导页（最顶层，覆盖全屏）
                    if (showOnboarding) {
                        OnboardingScreen(
                            onFinish = {
                                OnboardingStore.setCompleted(appContext, true)
                                showOnboarding = false
                            },
                        )
                    }

                    // 认证门控（最顶层）：检查会话→加载；待指纹解锁→指纹页；未登录→登录页
                    when {
                        authState.isCheckingSession -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFFBFAF7)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Color(0xFF3D7A5A))
                        }

                        authState.needsBiometricUnlock -> BiometricLockScreen(
                            isLoading = authState.isLoading,
                            errorMessage = authState.errorMessage,
                            onUnlock = { authViewModel.unlockWithBiometric(this@MainActivity) },
                            onUsePassword = { authViewModel.cancelBiometricUnlock() },
                            modifier = Modifier.fillMaxSize(),
                        )

                        !authState.isAuthenticated -> LoginRoute(
                            viewModel = authViewModel,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    /**
     * App 已在运行时再点链接进入（singleTask 复用实例）：更新目标页。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLinks.resolve(intent)?.let { deepLinkRoute = it }
    }

    /**
     * Android 13+（TIRAMISU）需运行时授予 POST_NOTIFICATIONS 才能显示通知。
     * 这里在未授权时直接请求；如需更友好的体验，可在请求前用
     * shouldShowRequestPermissionRationale 弹一段说明 UI。
     */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** 获取当前 FCM 注册令牌（用于测试/上报服务端）。 */
    private fun logFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) DebugLog.i("Fcm", "current token: ${task.result}")
            else DebugLog.w("Fcm", "fetch token failed: ${task.exception?.message}")
        }
    }
}
