package com.vpegs.ms.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val GlassScheme = darkColorScheme(
    primary = GlassPrimary,
    onPrimary = Color.White,
    primaryContainer = GlassPrimarySoft,
    secondary = GlassAccent,
    onSecondary = Color.Black,
    background = GlassBg,
    onBackground = GlassText,
    surface = GlassSurface,
    onSurface = GlassText,
    surfaceVariant = GlassSurfaceHigh,
    onSurfaceVariant = GlassTextMuted,
    outline = GlassBorder,
    error = GlassDanger
)

@Composable
fun VpEgsTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = GlassScheme, typography = Typography, content = content)
}
