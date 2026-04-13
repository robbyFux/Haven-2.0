package org.havenapp.main.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import org.havenapp.main.R
import org.havenapp.main.security.AppLockState
import org.havenapp.main.security.PinHashManager
import org.havenapp.main.ui.diagnostics.DiagnosticsScreen
import org.havenapp.main.ui.lock.PinLockScreen
import org.havenapp.main.ui.monitor.MonitorScreen
import org.havenapp.main.ui.settings.ExpertSettingsScreen
import org.havenapp.main.ui.settings.SettingsScreen
import org.havenapp.main.ui.settings.SettingsViewModel
import org.havenapp.main.ui.settings.ZoneEditorScreen
import org.havenapp.main.ui.timeline.EventDetailScreen
import org.havenapp.main.ui.timeline.TimelineScreen

object Routes {
    const val MONITOR = "monitor"
    const val TIMELINE = "timeline"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
    const val ZONE_EDITOR = "zone_editor"
    const val EXPERT_SETTINGS = "expert_settings"
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
    val locked by AppLockState.locked.collectAsStateWithLifecycle()
    val settingsVm: SettingsViewModel = hiltViewModel()
    // pinEnabled is null while DataStore hasn't emitted yet (loading state).
    val pinEnabled by settingsVm.pinEnabled.collectAsStateWithLifecycle()
    val pinHash by settingsVm.pinHash.collectAsStateWithLifecycle()
    val pinSalt by settingsVm.pinSalt.collectAsStateWithLifecycle()

    // If DataStore hasn't emitted yet, pinEnabled is null. Show nothing (blank screen)
    // while we wait — avoids the false-unlock window that let content flash before the
    // PIN screen appeared. AppLockState starts locked=true, so this is safe.
    if (pinEnabled == null) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    // Auto-unlock if PIN is disabled: AppLockState starts locked=true on every cold
    // start, so we must explicitly unlock once DataStore confirms PIN is off.
    LaunchedEffect(pinEnabled) {
        if (pinEnabled == false) {
            AppLockState.unlock()
        }
    }

    if (locked && pinEnabled == true && pinHash != null && pinSalt != null) {
        PinLockScreen(
            onVerify = { pin -> PinHashManager.verifyPin(pin, pinHash!!, pinSalt!!) },
            onUnlocked = { AppLockState.unlock() },
        )
        return
    }

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
                    onOpenExpertSettings = { navController.navigate(Routes.EXPERT_SETTINGS) },
                )
            }
            composable(Routes.ZONE_EDITOR) {
                ZoneEditorScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.EXPERT_SETTINGS) {
                ExpertSettingsScreen(onBack = { navController.popBackStack() })
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
