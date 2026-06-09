package com.novamind.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                var currentRoute by rememberSaveable {
                    mutableStateOf(BottomNavDestination.Home.route)
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentRoute) {
                        BottomNavDestination.Home.route -> HomeRoute()
                        BottomNavDestination.Create.route -> CreateRoute(
                            onBack = { currentRoute = BottomNavDestination.Home.route }
                        )
                        BottomNavDestination.Library.route -> LibraryRoute()
                        BottomNavDestination.Calendar.route -> CalendarRoute()
                    }

                    AppBottomNavBar(
                        currentRoute = currentRoute,
                        onNavigate = { route -> currentRoute = route },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}
