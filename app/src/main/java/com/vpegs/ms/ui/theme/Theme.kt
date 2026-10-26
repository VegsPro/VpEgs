package com.vpegs.ms.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun VpEgsTheme(
    themeId: AppThemeId = AppThemeId.DARK_BLUE,
    content: @Composable () -> Unit
) {
    val palette = paletteFor(themeId)
    applyPalette(palette)

    val scheme = if (palette.isLight) {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.primarySoft,
            secondary = palette.accent,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surfaceHigh,
            onSurfaceVariant = palette.textMuted,
            outline = palette.border,
            error = GlassDanger
        )
    } else {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.primarySoft,
            secondary = palette.accent,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surfaceHigh,
            onSurfaceVariant = palette.textMuted,
            outline = palette.border,
            error = GlassDanger
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = palette.isLight
        }
    }
    MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
}
