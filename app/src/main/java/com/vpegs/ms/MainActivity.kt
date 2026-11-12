package com.vpegs.ms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.*
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vpegs.ms.ui.screens.*
import com.vpegs.ms.ui.theme.*
import com.vpegs.ms.viewmodel.MainViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** Space reserved so list items can scroll above the floating tab. */
val BottomBarContentPad = 108.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            LaunchedEffect(Unit) { vm.load(applicationContext) }
            // Apply theme synchronously on every recomposition so colors update immediately
            // Theme without key() — keeps current screen (e.g. Settings) on change
            val themeId = vm.themeId
            val palette = paletteFor(themeId)
            applyPalette(palette)
            val view = LocalView.current
            SideEffect {
                val window = (view.context as android.app.Activity).window
                window.statusBarColor = palette.bg.toArgb()
                window.navigationBarColor = palette.bg.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = palette.isLight
            }

            MaterialTheme(
                colorScheme = if (palette.isLight) {
                    lightColorScheme(
                        background = palette.bg,
                        surface = palette.surface,
                        primary = palette.primary,
                        onBackground = palette.text,
                        onSurface = palette.text
                    )
                } else {
                    darkColorScheme(
                        background = palette.bg,
                        surface = palette.surface,
                        primary = palette.primary,
                        onBackground = palette.text,
                        onSurface = palette.text
                    )
                }
            ) {
                VpEgsApp(vm)
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Главная", Icons.Filled.Home)
    data object Servers : Screen("servers", "Серверы", Icons.Outlined.Dns)
    data object Subs : Screen("subs", "Подписки", Icons.Outlined.Subscriptions)
    data object Apps : Screen("apps", "Прилож.", Icons.Outlined.Apps)
    data object Settings : Screen("settings", "Настр.", Icons.Filled.Settings)
}

@Composable
fun VpEgsApp(vm: MainViewModel) {
    val nav = rememberNavController()
    val items = listOf(Screen.Home, Screen.Servers, Screen.Subs, Screen.Apps, Screen.Settings)
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val hazeState = remember { HazeState() }
    val glassOn = vm.glassEffect

    CompositionLocalProvider(
        LocalGlassEnabled provides glassOn,
        LocalHazeState provides hazeState
    ) {
        // Full-screen theme bg under status bar (no gray strip)
        Box(
            Modifier
                .fillMaxSize()
                .background(GlassBg)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .then(if (glassOn) Modifier.hazeSource(state = hazeState) else Modifier)
            ) {
                NavHost(
                    navController = nav,
                    startDestination = Screen.Home.route,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        fadeIn(tween(260)) + slideInHorizontally(tween(300)) { it / 10 }
                    },
                    exitTransition = {
                        fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { -it / 14 }
                    },
                    popEnterTransition = {
                        fadeIn(tween(260)) + slideInHorizontally(tween(300)) { -it / 10 }
                    },
                    popExitTransition = {
                        fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { it / 14 }
                    }
                ) {
                    composable(Screen.Home.route) { HomeScreen(vm) }
                    composable(Screen.Servers.route) { ServersScreen(vm) }
                    composable(Screen.Subs.route) { SubscriptionsScreen(vm) }
                    composable(Screen.Apps.route) { AppsScreen(vm) }
                    composable(Screen.Settings.route) { SettingsScreen(vm) }
                }
            }

            GlassBottomBar(
                items = items,
                currentRoute = route,
                glassOn = glassOn,
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                onSelect = { s ->
                    nav.navigate(s.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )

            if (vm.showHwidPrompt) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable { vm.dismissHwidPrompt() }
                ) {
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 88.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (GlassIsLight) Color.White else GlassSurfaceHigh)
                            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            "Серверов 0, попробуйте включить HWID",
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassText
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Панель вернула заглушку вместо списка серверов. Включите отправку HWID и подписка обновится автоматически.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassTextMuted
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Отправка HWID", color = GlassText)
                                Text("Для привязки к устройству", color = GlassTextMuted, style = MaterialTheme.typography.labelMedium)
                            }
                            Switch(
                                checked = vm.sendHwid,
                                onCheckedChange = { on ->
                                    if (on) vm.enableHwidAndRefresh()
                                    else vm.updateSendHwid(false)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = GlassPrimary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassBottomBar(
    items: List<Screen>,
    currentRoute: String?,
    glassOn: Boolean,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    onSelect: (Screen) -> Unit
) {
    val shape = RoundedCornerShape(32.dp)
    val barStyle = if (GlassIsLight) HazeStyle(
        backgroundColor = Color.White.copy(alpha = 0.55f),
        tints = listOf(
            HazeTint(Color.White.copy(alpha = 0.35f)),
            HazeTint(GlassPrimary.copy(alpha = 0.10f)),
            HazeTint(Color.Black.copy(alpha = 0.04f)),
        ),
        blurRadius = 48.dp,
    ) else HazeStyle(
        backgroundColor = Color(0xFF0B1220).copy(alpha = 0.55f),
        tints = listOf(
            HazeTint(Color.White.copy(alpha = 0.18f)),
            HazeTint(GlassPrimary.copy(alpha = 0.12f)),
            HazeTint(Color(0xFF020617).copy(alpha = 0.25f)),
        ),
        blurRadius = 48.dp,
    )

    val barMod = modifier
        .fillMaxWidth()
        .height(72.dp)
        .clip(shape)
        .then(
            if (glassOn) {
                val under = if (GlassIsLight) Color.White.copy(alpha = 0.78f) else Color(0xCC0B1220)
                val edge = if (GlassIsLight) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.22f)
                Modifier
                    .background(under, shape)
                    .hazeEffect(state = hazeState, style = barStyle)
                    .border(1.dp, edge, shape)
            } else {
                Modifier
                    .background(GlassSurface)
                    .border(1.dp, GlassBorder, shape)
            }
        )

    Row(
        barMod.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEach { s ->
            val selected = currentRoute == s.route
            val bg by animateColorAsState(
                if (selected) GlassPrimary.copy(alpha = 0.25f) else Color.Transparent,
                tween(220),
                label = "navBg"
            )
            val tint by animateColorAsState(
                if (selected) GlassPrimary else GlassTextMuted,
                tween(220),
                label = "navTint"
            )
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp, horizontal = 2.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(bg)
                    .clickable(indication = null, interactionSource = interaction) { onSelect(s) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(s.icon, s.title, tint = tint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        s.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
