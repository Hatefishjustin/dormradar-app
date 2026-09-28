package com.dormradar.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dormradar.app.feature.config.DeviceAdminScreen
import com.dormradar.app.feature.config.EngineeringScreen
import com.dormradar.app.feature.config.NoiseScreen
import com.dormradar.app.feature.config.ParamsScreen
import com.dormradar.app.feature.config.QualityScreen
import com.dormradar.app.feature.config.SensitivityScreen
import com.dormradar.app.feature.config.TemplateScreen
import com.dormradar.app.feature.deploy.DeployScreen
import com.dormradar.app.feature.events.EventsScreen
import com.dormradar.app.feature.guide.TuningGuideScreen
import com.dormradar.app.feature.home.HomeScreen
import com.dormradar.app.feature.logs.LogsScreen
import com.dormradar.app.feature.logs.DistanceDiagScreen
import com.dormradar.app.feature.quicksetup.QuickSetupScreen
import com.dormradar.app.feature.settings.SettingsScreen

private data class Tab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val TABS = listOf(
    Tab("home", "首页", Icons.Filled.Home, Icons.Outlined.Home),
    Tab("events", "事件", Icons.Filled.Notifications, Icons.Outlined.Notifications),
    Tab("settings", "设置", Icons.Filled.Settings, Icons.Outlined.Settings),
)

/** 顶层 Tab 路由：只有这些页面显示底部栏（详细设置子页深层收纳，PRD 4.0） */
private val TOP_LEVEL_ROUTES = setOf("home", "events", "settings")

/**
 * 应用骨架：底部 3 Tab（首页/事件/设置）+ 详细设置子路由（PRD 4.0 两层信息架构）。
 * 子页面不显示底部栏，营造"深入设置"的层次感。
 */
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = currentDestination?.route in TOP_LEVEL_ROUTES

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TABS.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") { HomeScreen(onNavigate = { navController.navigate(it) }) }
            composable("events") { EventsScreen() }
            composable("settings") {
                SettingsScreen(onNavigate = { navController.navigate(it) })
            }

            // ---- 详细设置子页（深层收纳）----
            composable("quicksetup") { QuickSetupScreen(onBack = { navController.popBackStack() }, onNavigate = { navController.navigate(it) }) }
            composable("params") { ParamsScreen(onBack = { navController.popBackStack() }) }
            composable("sensitivity") { SensitivityScreen(onBack = { navController.popBackStack() }) }
            composable("engineering") { EngineeringScreen(onBack = { navController.popBackStack() }) }
            composable("noise") { NoiseScreen(onBack = { navController.popBackStack() }) }
            composable("device") { DeviceAdminScreen(onBack = { navController.popBackStack() }) }
            composable("logs") { LogsScreen(onBack = { navController.popBackStack() }) }
            composable("diag") { DistanceDiagScreen(onBack = { navController.popBackStack() }) }
            composable("templates") { TemplateScreen(onBack = { navController.popBackStack() }) }
            composable("deploy") { DeployScreen(onBack = { navController.popBackStack() }) }
            composable("quality") { QualityScreen(onBack = { navController.popBackStack() }) }
            composable("tuning") { TuningGuideScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
