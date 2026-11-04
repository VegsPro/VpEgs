package com.vpegs.ms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            LaunchedEffect(Unit) { vm.load(applicationContext) }
            LaunchedEffect(vm.themeId) { applyPalette(paletteFor(vm.themeId)) }
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = GlassBg,
                    surface = GlassSurface,
                    primary = GlassPrimary
                )
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
        LocalHazeState provides if (glassOn) hazeState else null
    ) {
        Box(Modifier.fillMaxSize()) {
            // Ambient gradient background (shows through glass)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                GlassBg,
                                GlassPrimary.copy(alpha = 0.12f),
                                GlassBg,
                                GlassAccent.copy(alpha = 0.08f),
                                GlassBg
                            )
                        )
                    )
                    .then(if (glassOn) Modifier.hazeSource(state = hazeState) else Modifier)
            )

            NavHost(
                navController = nav,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 88.dp),
                enterTransition = {
                    fadeIn(tween(280)) + slideInHorizontally(tween(320)) { it / 8 }
                },
                exitTransition = {
                    fadeOut(tween(200)) + slideOutHorizontally(tween(280)) { -it / 12 }
                },
                popEnterTransition = {
                    fadeIn(tween(280)) + slideInHorizontally(tween(320)) { -it / 8 }
                },
                popExitTransition = {
                    fadeOut(tween(200)) + slideOutHorizontally(tween(280)) { it / 12 }
                }
            ) {
                composable(Screen.Home.route) { HomeScreen(vm) }
                composable(Screen.Servers.route) { ServersScreen(vm) }
                composable(Screen.Subs.route) { SubscriptionsScreen(vm) }
                composable(Screen.Apps.route) { AppsScreen(vm) }
                composable(Screen.Settings.route) { SettingsScreen(vm) }
            }

            // Floating glass bottom bar (like music app screenshot)
            GlassBottomBar(
                items = items,
                currentRoute = route,
                glassOn = glassOn,
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                onSelect = { s ->
                    nav.navigate(s.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
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
    val shape = RoundedCornerShape(28.dp)
    val barMod = modifier
        .fillMaxWidth()
        .height(64.dp)
        .clip(shape)
        .then(
            if (glassOn) {
                Modifier
                    .hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = Color.Black.copy(alpha = 0.25f),
                            tints = listOf(
                                HazeTint(Color.White.copy(alpha = 0.08f)),
                                HazeTint(GlassPrimary.copy(alpha = 0.12f))
                            ),
                            blurRadius = 24.dp,
                        )
                    )
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
            } else {
                Modifier
                    .background(GlassSurface)
                    .border(1.dp, GlassBorder, shape)
            }
        )

    Row(
        barMod.padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEach { s ->
            val selected = currentRoute == s.route
            val bg by animateColorAsState(
                if (selected) GlassPrimary.copy(alpha = 0.22f) else Color.Transparent,
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
                    .clip(RoundedCornerShape(20.dp))
                    .background(bg)
                    .clickable(indication = null, interactionSource = interaction) { onSelect(s) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(s.icon, s.title, tint = tint, modifier = Modifier.size(22.dp))
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
