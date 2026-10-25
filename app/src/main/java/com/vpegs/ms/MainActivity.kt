package com.vpegs.ms

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vpegs.ms.ui.screens.*
import com.vpegs.ms.ui.theme.GlassBg
import com.vpegs.ms.ui.theme.GlassPrimary
import com.vpegs.ms.ui.theme.GlassPrimarySoft
import com.vpegs.ms.ui.theme.GlassSurface
import com.vpegs.ms.ui.theme.GlassTextMuted
import com.vpegs.ms.ui.theme.VpEgsTheme
import com.vpegs.ms.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private var vmRef: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VpEgsTheme {
                val vm: MainViewModel = viewModel()
                LaunchedEffect(Unit) { vm.load(applicationContext) }
                vmRef = vm
                VpEgsApp(vm)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MainViewModel.REQ_VPN) {
            vmRef?.onVpnPermissionResult(this, resultCode)
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Главная", Icons.Filled.Home)
    data object Servers : Screen("servers", "Серверы", Icons.Outlined.Dns)
    data object Subs : Screen("subs", "Подписки", Icons.Outlined.Subscriptions)
    data object Apps : Screen("apps", "Приложения", Icons.Outlined.Apps)
    data object Settings : Screen("settings", "Настройки", Icons.Filled.Settings)
}

@Composable
fun VpEgsApp(vm: MainViewModel) {
    val nav = rememberNavController()
    val items = listOf(Screen.Home, Screen.Servers, Screen.Subs, Screen.Apps, Screen.Settings)
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    Scaffold(
        containerColor = GlassBg,
        bottomBar = {
            NavigationBar(containerColor = GlassSurface, contentColor = GlassPrimarySoft) {
                items.forEach { s ->
                    NavigationBarItem(
                        selected = route == s.route,
                        onClick = {
                            nav.navigate(s.route) {
                                popUpTo(nav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(s.icon, s.title) },
                        label = { Text(s.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = GlassPrimary,
                            selectedTextColor = GlassPrimarySoft,
                            indicatorColor = GlassPrimary.copy(0.18f),
                            unselectedIconColor = GlassTextMuted,
                            unselectedTextColor = GlassTextMuted
                        )
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, Screen.Home.route, Modifier.padding(pad)) {
            composable(Screen.Home.route) { HomeScreen(vm) }
            composable(Screen.Servers.route) { ServersScreen(vm) }
            composable(Screen.Subs.route) { SubscriptionsScreen(vm) }
            composable(Screen.Apps.route) { AppsScreen(vm) }
            composable(Screen.Settings.route) { SettingsScreen(vm) }
        }
    }
}
