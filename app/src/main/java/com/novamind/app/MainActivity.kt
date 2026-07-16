package com.novamind.app

import com.novamind.app.common.log.AppLog
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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import com.novamind.app.common.deeplink.DeepLinkTarget
import com.novamind.app.common.deeplink.DeepLinks
import com.novamind.app.common.onboarding.OnboardingScreen
import com.novamind.app.common.onboarding.OnboardingStore
import com.novamind.app.common.update.UpdateController
import com.novamind.app.common.update.UpdateDialog
import com.novamind.app.feature.calendar.CalendarRoute
import com.novamind.app.feature.create.CreateRoute
import com.novamind.app.feature.home.HomeRoute
import com.novamind.app.feature.library.LibraryRoute
import com.novamind.app.feature.profile.ProfileRoute
import com.novamind.app.feature.home.AboutMyNovieScreen
import com.novamind.app.common.permission.PermissionManagerScreen
import com.novamind.app.feature.recyclebin.RecycleBinRoute
import com.novamind.app.feature.create.tag.tagmanager.TagManagerRoute
import com.novamind.app.feature.asknovie.AskNovieScreen
import com.novamind.app.feature.auth.AuthViewModel
import com.novamind.app.feature.auth.BiometricLockScreen
import com.novamind.app.feature.auth.LoginRoute
import com.novamind.app.ui.components.AppBottomNavBar
import com.novamind.app.ui.components.BottomNavDestination
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.ui.theme.rememberIsDarkTheme
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novamind.app.common.session.UserSessionManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.AndroidEntryPoint

// 导航顺序，用于判断滑动方向
private val navOrder = listOf(
    BottomNavDestination.Home.route,
    BottomNavDestination.Calendar.route,
    BottomNavDestination.Create.route,
    BottomNavDestination.Library.route,
    BottomNavDestination.Profile.route,
)

// Hilt 入口：使 viewModel() 支持 @HiltViewModel
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    // App Link 目标：onCreate/onNewIntent 写入，Compose 侧消费后清空
    private var deepLinkTarget by mutableStateOf<DeepLinkTarget?>(null)

    // Android 13+ 通知权限；结果不阻塞主流程
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            AppLog.i("Fcm") { "POST_NOTIFICATIONS granted=$granted" }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkTarget = DeepLinks.resolve(intent)
        askNotificationPermission()
        logFcmToken()
        // adjustNothing：键盘弹出不重排布局，光标遮挡由编辑器自行滚动
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        setContent {
            AppTheme(darkTheme = rememberIsDarkTheme()) {
                var currentRoute by rememberSaveable {
                    mutableStateOf(BottomNavDestination.Home.route)
                }
                var editingNoteId by rememberSaveable { mutableStateOf<String?>(null) }
                // Create 的来源页，返回时回到该页
                var createReturnRoute by rememberSaveable {
                    mutableStateOf(BottomNavDestination.Home.route)
                }
                // Library 作为子页进入（首页 See all）：显示返回键、可回上一页
                var libraryAsSubpage by rememberSaveable { mutableStateOf(false) }
                // 全屏页（图片预览等）打开时隐藏底栏
                var hideBottomNav by rememberSaveable { mutableStateOf(false) }
                // 三个全屏覆盖层：Ask Novie（底栏品牌按钮）、回收站 / 标签管理（Library 侧栏）
                var showAskNovie by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = showAskNovie) { showAskNovie = false }
                var showRecycleBin by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = showRecycleBin) { showRecycleBin = false }
                var showTagManager by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = showTagManager) { showTagManager = false }
                // Profile 页入口的全屏覆盖层：权限管理 / 关于
                var showProfilePermissions by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = showProfilePermissions) { showProfilePermissions = false }
                var showAbout by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = showAbout) { showAbout = false }
                // Library 子页时，系统返回与左上角返回键行为一致
                BackHandler(
                    enabled = libraryAsSubpage && currentRoute == BottomNavDestination.Library.route,
                ) {
                    libraryAsSubpage = false
                    currentRoute = BottomNavDestination.Home.route
                }
                // 首启引导页
                val appContext = LocalContext.current
                var showOnboarding by rememberSaveable {
                    mutableStateOf(!OnboardingStore.isCompleted(appContext))
                }

                // 认证状态（Auth0）：未登录时用登录页门控
                val authViewModel: AuthViewModel = viewModel()
                val authState by authViewModel.uiState.collectAsStateWithLifecycle()
                // 用户档案单一数据源：昵称/邮箱从全局会话派生（不再放在 AuthUiState）。
                val userSession by UserSessionManager.session.collectAsStateWithLifecycle()

                // 前后台监听：后台超时后回前台触发生物识别上锁
                DisposableEffect(authViewModel) {
                    val owner = ProcessLifecycleOwner.get()
                    val observer = object : DefaultLifecycleObserver {
                        override fun onStop(owner: LifecycleOwner) = authViewModel.onAppBackgrounded()
                        override fun onStart(owner: LifecycleOwner) = authViewModel.onAppForegrounded()
                    }
                    owner.lifecycle.addObserver(observer)
                    onDispose { owner.lifecycle.removeObserver(observer) }
                }

                // 冷启动检查升级（Mock 策略，Debug 工具箱可模拟）
                LaunchedEffect(Unit) { UpdateController.checkOnStartup(appContext, BuildConfig.VERSION_CODE) }

                // App Link：切到目标页并退出全部覆盖层，消费后清空防重复触发
                LaunchedEffect(deepLinkTarget) {
                    deepLinkTarget?.let { target ->
                        when (target) {
                            is DeepLinkTarget.Tab -> {
                                currentRoute = target.route
                                editingNoteId = null
                            }
                            is DeepLinkTarget.Note -> {
                                editingNoteId = target.noteId
                                createReturnRoute = BottomNavDestination.Home.route
                                currentRoute = BottomNavDestination.Create.route
                            }
                        }
                        showAskNovie = false
                        showRecycleBin = false
                        showTagManager = false
                        deepLinkTarget = null
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // 按路由保存/恢复各页可保存状态（含滚动位置）：从详情返回首页时保留滚动进度。
                    val saveableStateHolder = rememberSaveableStateHolder()
                    AnimatedContent(
                        targetState = currentRoute,
                        modifier = Modifier.fillMaxSize(),   // 给子页面有界高度（CreateScreen 的 weight 依赖）
                        transitionSpec = {
                            val createRoute = BottomNavDestination.Create.route
                            val involvesCreate = targetState == createRoute || initialState == createRoute
                            if (!involvesCreate) {
                                // Home / Calendar / Library / Profile 之间：直接切换，无动画
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                val fromIndex = navOrder.indexOf(initialState)
                                val toIndex = navOrder.indexOf(targetState)
                                // Create 视作详情页：进从右入、返向右出，不受 navOrder 影响
                                val forward = when {
                                    targetState == createRoute -> true
                                    initialState == createRoute -> false
                                    else -> toIndex >= fromIndex
                                }
                                if (forward) {
                                    (slideInHorizontally { it } + fadeIn(initialAlpha = 0.3f))
                                        .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
                                } else {
                                    (slideInHorizontally { -it / 3 } + fadeIn(initialAlpha = 0.3f))
                                        .togetherWith(slideOutHorizontally { it } + fadeOut())
                                }
                            }
                        },
                        label = "page_transition",
                    ) { route ->
                        saveableStateHolder.SaveableStateProvider(route) {
                        when (route) {
                            BottomNavDestination.Home.route -> HomeRoute(
                                onNoteClick = { noteId ->
                                    editingNoteId = noteId
                                    createReturnRoute = BottomNavDestination.Home.route
                                    currentRoute = BottomNavDestination.Create.route
                                },
                                onNotesSeeAll = {
                                    libraryAsSubpage = true
                                    currentRoute = BottomNavDestination.Library.route
                                },
                                // Up next 首卡「Start notes」→ 新建笔记
                                onStartNotes = {
                                    editingNoteId = null
                                    createReturnRoute = BottomNavDestination.Home.route
                                    currentRoute = BottomNavDestination.Create.route
                                },
                                onFullscreenChange = { hideBottomNav = it },
                                onLogout = { authViewModel.logout(this@MainActivity) },
                                onSwitchToLogin = { authViewModel.exitGuest() },
                                userName = userSession.profile?.displayName,
                                userEmail = userSession.profile?.email ?: userSession.userKey,
                                isGuest = authState.isGuest,
                                biometricAvailable = authState.biometricAvailable,
                                biometricEnabled = authState.biometricEnabled,
                                onToggleBiometric = { authViewModel.setBiometricEnabled(it) },
                            )
                            BottomNavDestination.Create.route -> CreateRoute(
                                noteId = editingNoteId,
                                onBack = {
                                    editingNoteId = null
                                    currentRoute = createReturnRoute
                                },
                                onFullscreenChange = { hideBottomNav = it },
                            )
                            BottomNavDestination.Library.route -> LibraryRoute(
                                onCreateNote = {
                                    editingNoteId = null
                                    createReturnRoute = BottomNavDestination.Library.route
                                    currentRoute = BottomNavDestination.Create.route
                                },
                                onOpenNote = { noteId ->
                                    editingNoteId = noteId
                                    createReturnRoute = BottomNavDestination.Library.route
                                    currentRoute = BottomNavDestination.Create.route
                                },
                                // 抽屉打开时隐藏底栏，让抽屉盖住底栏
                                onFullscreenChange = { hideBottomNav = it },
                                onOpenRecycleBin = { showRecycleBin = true },
                                onOpenTagManager = { showTagManager = true },
                                // 子页进入时提供返回；底栏进入无返回键
                                onBack = if (libraryAsSubpage) {
                                    {
                                        libraryAsSubpage = false
                                        currentRoute = BottomNavDestination.Home.route
                                    }
                                } else {
                                    null
                                },
                            )
                            BottomNavDestination.Calendar.route -> CalendarRoute(
                                onFullscreenChange = { hideBottomNav = it },
                            )
                            BottomNavDestination.Profile.route -> ProfileRoute(
                                userName = userSession.profile?.displayName,
                                userEmail = userSession.profile?.email ?: userSession.userKey,
                                isGuest = authState.isGuest,
                                appVersion = "v${BuildConfig.VERSION_NAME}",
                                onOpenPermissions = { showProfilePermissions = true },
                                onAbout = { showAbout = true },
                                onLogout = { authViewModel.logout(this@MainActivity) },
                                onLogin = { authViewModel.exitGuest() },
                            )
                        }
                        }
                    }

                    // 底部导航栏：全屏页或 Create 编辑页时滑出隐藏
                    AnimatedVisibility(
                        visible = !hideBottomNav && currentRoute != BottomNavDestination.Create.route,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        AppBottomNavBar(
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                // 底栏进入 Library 清掉子页标记
                                if (route == BottomNavDestination.Library.route) libraryAsSubpage = false
                                // 已在当前页不重复跳转
                                if (route == currentRoute) return@AppBottomNavBar
                                currentRoute = route
                            },
                            // 中央「+」速拨：新建笔记
                            onCreate = {
                                editingNoteId = null
                                createReturnRoute = currentRoute
                                currentRoute = BottomNavDestination.Create.route
                            },
                            // 中央「+」速拨：Ask Novie
                            onAskNovie = { showAskNovie = true },
                        )
                    }

                    // Ask Novie（全屏覆盖，自带返回）
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

                    // 回收站（全屏覆盖，自带返回）
                    AnimatedVisibility(
                        visible = showRecycleBin,
                        enter = slideInHorizontally { it } + fadeIn(),
                        exit = slideOutHorizontally { it } + fadeOut(),
                    ) {
                        RecycleBinRoute(
                            onBack = { showRecycleBin = false },
                            onFullscreenChange = { hideBottomNav = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // 标签管理（全屏覆盖，自带返回）
                    AnimatedVisibility(
                        visible = showTagManager,
                        enter = slideInHorizontally { it } + fadeIn(),
                        exit = slideOutHorizontally { it } + fadeOut(),
                    ) {
                        TagManagerRoute(
                            onBack = { showTagManager = false },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // 权限管理（Profile 页入口，全屏覆盖）
                    AnimatedVisibility(
                        visible = showProfilePermissions,
                        enter = slideInHorizontally { it } + fadeIn(),
                        exit = slideOutHorizontally { it } + fadeOut(),
                    ) {
                        PermissionManagerScreen(
                            onBack = { showProfilePermissions = false },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // 关于 MyNovie（Profile 页入口，全屏覆盖）
                    AnimatedVisibility(
                        visible = showAbout,
                        enter = slideInHorizontally { it } + fadeIn(),
                        exit = slideOutHorizontally { it } + fadeOut(),
                    ) {
                        AboutMyNovieScreen(
                            versionName = BuildConfig.VERSION_NAME,
                            versionCode = BuildConfig.VERSION_CODE,
                            onBack = { showAbout = false },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // Debug 工具箱（摇一摇打开）：实现只在 src/debug，release 为空实现
                    DebugOverlay(onNavigate = { route ->
                        editingNoteId = null
                        currentRoute = route
                    })

                    // 升级弹窗（强制升级时不可关闭）
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

                    // 首启引导页（最顶层）
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

    /** App 运行中再点链接（singleTask 复用实例）：更新目标页。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLinks.resolve(intent)?.let { deepLinkTarget = it }
    }

    /** Android 13+ 需运行时授予 POST_NOTIFICATIONS；未授权时直接请求。 */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** 获取当前 FCM 注册令牌（用于测试/上报服务端）。 */
    private fun logFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) AppLog.i("Fcm") { "current token: ${task.result}" }
            else AppLog.w("Fcm") { "fetch token failed: ${task.exception?.message}" }
        }
    }
}
