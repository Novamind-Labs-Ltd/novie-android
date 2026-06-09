package com.novamind.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        setContent {
            AppTheme {
                var currentRoute by rememberSaveable {
                    mutableStateOf(BottomNavDestination.Home.route)
                }
                var editingNoteId by rememberSaveable { mutableStateOf<String?>(null) }

                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = currentRoute,
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
                                }
                            )
                            BottomNavDestination.Create.route -> CreateRoute(
                                noteId = editingNoteId,
                                onBack = {
                                    editingNoteId = null
                                    currentRoute = BottomNavDestination.Home.route
                                }
                            )
                            BottomNavDestination.Library.route -> LibraryRoute()
                            BottomNavDestination.Calendar.route -> CalendarRoute()
                        }
                    }

                    AppBottomNavBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            if (route == BottomNavDestination.Create.route) editingNoteId = null
                            currentRoute = route
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}
