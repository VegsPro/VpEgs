package com.vpegs.ms.ui.theme

import androidx.compose.ui.graphics.Color

// Mutable theme tokens (updated by applyPalette)
var GlassBg = Color(0xFF020617)
var GlassSurface = Color(0xCC0F172A)
var GlassSurfaceHigh = Color(0xE61E293B)
var GlassBorder = Color(0x33FFFFFF)
var GlassPrimary = Color(0xFF38BDF8)
var GlassPrimarySoft = Color(0x3338BDF8)
var GlassAccent = Color(0xFF22D3EE)
var GlassText = Color(0xFFF1F5F9)
var GlassTextMuted = Color(0xFF94A3B8)
var GlassDanger = Color(0xFFF87171)
var GlassWarning = Color(0xFFFBBF24)
var GlassSuccess = Color(0xFF4ADE80)

enum class AppThemeId(val title: String) {
    DARK_BLUE("Тёмная синяя"),
    DARK_PURPLE("Тёмная фиолет."),
    DARK_GREEN("Тёмная зелёная"),
    DARK_ORANGE("Тёмная оранж."),
    LIGHT_BLUE("Светлая синяя"),
    LIGHT_PURPLE("Светлая фиолет."),
    LIGHT_GRAY("Светлая серая");
}

data class ThemePalette(
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val border: Color,
    val primary: Color,
    val primarySoft: Color,
    val accent: Color,
    val text: Color,
    val textMuted: Color,
    val isLight: Boolean
)

fun paletteFor(id: AppThemeId): ThemePalette = when (id) {
    AppThemeId.DARK_BLUE -> ThemePalette(
        Color(0xFF020617), Color(0xCC0F172A), Color(0xE61E293B), Color(0x33FFFFFF),
        Color(0xFF38BDF8), Color(0x3338BDF8), Color(0xFF22D3EE),
        Color(0xFFF1F5F9), Color(0xFF94A3B8), false
    )
    AppThemeId.DARK_PURPLE -> ThemePalette(
        Color(0xFF0B0614), Color(0xCC1A1028), Color(0xE62E1B45), Color(0x33FFFFFF),
        Color(0xFFA78BFA), Color(0x33A78BFA), Color(0xFFC084FC),
        Color(0xFFF5F3FF), Color(0xFFA5A0B8), false
    )
    AppThemeId.DARK_GREEN -> ThemePalette(
        Color(0xFF03120A), Color(0xCC0C1F16), Color(0xE6143324), Color(0x33FFFFFF),
        Color(0xFF34D399), Color(0x3334D399), Color(0xFF6EE7B7),
        Color(0xFFECFDF5), Color(0xFF8BA89A), false
    )
    AppThemeId.DARK_ORANGE -> ThemePalette(
        Color(0xFF120A03), Color(0xCC1F140C), Color(0xE6332414), Color(0x33FFFFFF),
        Color(0xFFFB923C), Color(0x33FB923C), Color(0xFFFBBF24),
        Color(0xFFFFF7ED), Color(0xFFA89B8B), false
    )
    AppThemeId.LIGHT_BLUE -> ThemePalette(
        Color(0xFFF0F9FF), Color(0xE6FFFFFF), Color(0xFFF1F5F9), Color(0x22000000),
        Color(0xFF0284C7), Color(0x330284C7), Color(0xFF0EA5E9),
        Color(0xFF0F172A), Color(0xFF64748B), true
    )
    AppThemeId.LIGHT_PURPLE -> ThemePalette(
        Color(0xFFFAF5FF), Color(0xE6FFFFFF), Color(0xFFF5F3FF), Color(0x22000000),
        Color(0xFF7C3AED), Color(0x337C3AED), Color(0xFFA855F7),
        Color(0xFF1E1B2E), Color(0xFF6B7280), true
    )
    AppThemeId.LIGHT_GRAY -> ThemePalette(
        Color(0xFFF8FAFC), Color(0xE6FFFFFF), Color(0xFFF1F5F9), Color(0x22000000),
        Color(0xFF334155), Color(0x33334155), Color(0xFF64748B),
        Color(0xFF0F172A), Color(0xFF64748B), true
    )
}

fun applyPalette(p: ThemePalette) {
    GlassBg = p.bg
    GlassSurface = p.surface
    GlassSurfaceHigh = p.surfaceHigh
    GlassBorder = p.border
    GlassPrimary = p.primary
    GlassPrimarySoft = p.primarySoft
    GlassAccent = p.accent
    GlassText = p.text
    GlassTextMuted = p.textMuted
}
