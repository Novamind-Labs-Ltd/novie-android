package com.novamind.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.novamind.app.common.onboarding.OnboardingScreen
import com.novamind.app.common.onboarding.OnboardingStore
import com.novamind.app.common.update.UpdateController
import com.novamind.app.common.update.UpdateDialog
import com.novamind.app.debug.DebugPanel
import com.novamind.app.debug.ShakeDetector
import com.novamind.app.feature.calendar.CalendarRoute
import com.novamind.app.feature.create.CreateRoute
import com.novamind.app.feature.home.HomeRoute
import com.novamind.app.feature.library.LibraryRoute
import com.novamind.app.ui.components.AppBottomNavBar
import com.novamind.app.ui.components.BottomNavDestination
import com.novamind.app.ui.theme.AppTheme

// 导航顺序，用于判断滑动方向
private val navOrder = listOf(
    BottomNavDestination.Brand.route,
    BottomNavDestination.Home.route,
    BottomNavDestination.Create.route,
    BottomNavDestination.Library.route,
    BottomNavDestination.Calendar.route,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                // 首启引导页
                val appContext = LocalContext.current
                var showOnboarding by rememberSaveable {
                    mutableStateOf(!OnboardingStore.isCompleted(appContext))
                }

                // 冷启动检查升级（Mock 策略；可在 Debug 工具箱模拟）
                LaunchedEffect(Unit) { UpdateController.checkOnStartup(BuildConfig.VERSION_CODE) }

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
                            )
                            BottomNavDestination.Create.route -> CreateRoute(
                                noteId = editingNoteId,
                                onBack = {
                                    editingNoteId = null
                                    currentRoute = BottomNavDestination.Home.route
                                },
                                onFullscreenChange = { hideBottomNav = it },
                            )
                            BottomNavDestination.Library.route -> LibraryRoute()
                            BottomNavDestination.Calendar.route -> CalendarRoute()
                        }
                    }

                    // 全屏页（如图片预览）打开时滑出隐藏底部导航栏
                    AnimatedVisibility(
                        visible = !hideBottomNav,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        AppBottomNavBar(
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                // 已在当前页（如编辑中点 Create）→ 保持不变，不重置不跳转
                                if (route == currentRoute) return@AppBottomNavBar
                                if (route == BottomNavDestination.Create.route) editingNoteId = null
                                currentRoute = route
                            },
                        )
                    }

                    // Debug 工具箱（仅 Debug 包）：摇一摇打开
                    if (BuildConfig.DEBUG) {
                        var showDebug by rememberSaveable { mutableStateOf(false) }
                        ShakeDetector(enabled = true) { showDebug = true }
                        if (showDebug) {
                            DebugPanel(
                                onDismiss = { showDebug = false },
                                onNavigate = { route ->
                                    editingNoteId = null
                                    currentRoute = route
                                    showDebug = false
                                },
                            )
                        }
                    }

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
                }
            }
        }
    }
}
