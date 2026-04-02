package org.havenapp.main.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import org.havenapp.main.R
import org.havenapp.main.ui.diagnostics.DiagnosticsScreen
import org.havenapp.main.ui.monitor.MonitorScreen
import org.havenapp.main.ui.settings.SettingsScreen
import org.havenapp.main.ui.settings.ZoneEditorScreen
import org.havenapp.main.ui.timeline.EventDetailScreen
import org.havenapp.main.ui.timeline.TimelineScreen

object Routes {
    const val MONITOR = "monitor"
    const val TIMELINE = "timeline"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
    const val ZONE_EDITOR = "zone_editor"
    const val EVENT_DETAIL = "event/{eventId}"

    fun eventDetail(eventId: Long) = "event/$eventId"
}

private data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val labelRes: Int,
)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.MONITOR, Icons.Filled.Shield, R.string.nav_monitor),
    BottomNavItem(Routes.TIMELINE, Icons.Filled.History, R.string.nav_timeline),
    BottomNavItem(Routes.SETTINGS, Icons.Filled.Settings, R.string.nav_settings),
)

private val bottomNavRoutes = setOf(Routes.MONITOR, Routes.TIMELINE, Routes.SETTINGS)

@Composable
fun HavenNavGraph(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomNavRoutes) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = {
                                Icon(item.icon, contentDescription = stringResource(item.labelRes))
                            },
                            label = { Text(stringResource(item.labelRes)) },
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(Routes.MONITOR) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.MONITOR,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.MONITOR) {
                MonitorScreen(onOpenZoneEditor = { navController.navigate(Routes.ZONE_EDITOR) })
            }
            composable(Routes.TIMELINE) {
                TimelineScreen(
                    onOpenDetail = { eventId -> navController.navigate(Routes.eventDetail(eventId)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                    onOpenZoneEditor = { navController.navigate(Routes.ZONE_EDITOR) },
                )
            }
            composable(Routes.ZONE_EDITOR) {
                ZoneEditorScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.DIAGNOSTICS) {
                DiagnosticsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.EVENT_DETAIL,
                arguments = listOf(navArgument("eventId") { type = NavType.LongType }),
            ) {
                EventDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
