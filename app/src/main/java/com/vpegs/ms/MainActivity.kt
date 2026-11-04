package com.vpegs.ms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        // Cards stay translucent only — real blur is for the floating bar (SimpMusic pattern:
        // blur target must be a sibling of hazeSource, not a child).
        LocalHazeState provides null
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(GlassBg) // solid theme bg — no weird gradient
        ) {
            // CONTENT = glass source (what gets blurred under the bar)
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (glassOn) Modifier.hazeSource(state = hazeState) else Modifier)
                    .padding(bottom = 88.dp)
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

            // FLOATING GLASS BAR = sibling of source (SimpMusic / Haze correct usage)
            GlassBottomBar(
                items = items,
                currentRoute = route,
                glassOn = glassOn,
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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

/**
 * Capsule bottom bar — same idea as SimpMusic mini-player / chip row:
 * hazeSource is the page; this bar applies blur as a sibling.
 */
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
    // ultraThin-like style (SimpMusic HazeMaterials.ultraThin analogue)
    val barStyle = HazeStyle(
        backgroundColor = GlassSurface.copy(alpha = 0.55f),
        tints = listOf(
            HazeTint(Color.White.copy(alpha = 0.12f)),
            HazeTint(GlassPrimary.copy(alpha = 0.06f)),
        ),
        blurRadius = 28.dp,
    )

    val barMod = modifier
        .fillMaxWidth()
        .height(64.dp)
        .clip(shape)
        .then(
            if (glassOn) {
                Modifier
                    .hazeEffect(state = hazeState, style = barStyle)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
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
