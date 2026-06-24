package com.quietping.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.quietping.domain.settings.ThemeSettings

/**
 * The Dark Liquid-Glass color scheme (DESIGN.md §2). Emerald primary, teal
 * secondary, near-black background, raised gray surfaces. [accent] overrides the
 * primary accent when the user picks a custom color.
 */
private fun quietPingDarkColors(accent: Color, secondary: Color) = darkColorScheme(
    primary = accent,
    onPrimary = OnAccent,
    primaryContainer = Emerald600,
    onPrimaryContainer = Emerald50,
    secondary = secondary,
    onSecondary = OnAccent,
    secondaryContainer = Teal700,
    onSecondaryContainer = Teal50,
    tertiary = Teal300,
    onTertiary = OnAccent,
    background = BgPrimary,
    onBackground = TextPrimary,
    surface = BgSecondary,
    onSurface = TextPrimary,
    surfaceVariant = BgTertiary,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = BgPrimary,
    surfaceContainerLow = BgSecondary,
    surfaceContainer = BgSecondary,
    surfaceContainerHigh = BgTertiary,
    surfaceContainerHighest = BgTertiary,
    outline = BorderSubtle,
    outlineVariant = Divider,
    error = StatusError,
    onError = TextPrimary,
    inverseSurface = TextPrimary,
    inverseOnSurface = BgPrimary,
    scrim = Color(0xCC000000)
)

/**
 * Optional light scheme (DESIGN.md is dark-first; this is the user opt-in). Keeps
 * the same accent family on a light canvas.
 */
private fun quietPingLightColors(accent: Color, secondary: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = Emerald100,
    onPrimaryContainer = Emerald900,
    secondary = secondary,
    onSecondary = Color.White,
    secondaryContainer = Teal100,
    onSecondaryContainer = Teal900,
    tertiary = Teal600,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0B1120),
    surface = Color.White,
    onSurface = Color(0xFF0B1120),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0x1F0B1120),
    outlineVariant = Color(0x140B1120),
    error = StatusError,
    onError = Color.White
)

/**
 * Resolved, structured runtime theme values that are not expressible through the
 * Material [androidx.compose.material3.ColorScheme] alone — exposed via
 * [LocalQuietPingTheme] so components can read glass intensity and the motion gate.
 *
 * @param glassIntensity 0f..1f multiplier for [Modifier.glass].
 * @param motionEnabled  whether spring/animation motion should run.
 * @param accent         the active accent color (mirrors colorScheme.primary).
 * @param secondary      the active secondary color.
 */
data class QuietPingThemeState(
    val glassIntensity: Float = 1f,
    val motionEnabled: Boolean = true,
    val accent: Color = Emerald400,
    val secondary: Color = Teal500
)

/** Ambient access to [QuietPingThemeState]; defaults to the canonical dark tokens. */
val LocalQuietPingTheme = staticCompositionLocalOf { QuietPingThemeState() }

/**
 * Root theme for QuietPing. Applies the Dark Liquid-Glass Material3 scheme, the
 * DESIGN.md typography, and publishes [QuietPingThemeState] for glass/motion.
 *
 * @param themeSettings persisted user appearance settings; when null, canonical
 *                      defaults are used (dark, emerald accent, full glass, motion on).
 */
@Composable
fun QuietPingTheme(
    themeSettings: ThemeSettings? = null,
    content: @Composable () -> Unit
) {
    val settings = themeSettings ?: ThemeSettings()
    val accent = parseHexColor(settings.accentHex, fallback = Emerald400)
    // Keep the teal companion fixed; only the primary accent is user-customizable.
    val secondary = Teal500

    val colorScheme =
        if (settings.darkMode) quietPingDarkColors(accent, secondary)
        else quietPingLightColors(accent, secondary)

    val state = QuietPingThemeState(
        glassIntensity = settings.glassIntensity.coerceIn(0f, 1f),
        motionEnabled = settings.motionEnabled,
        accent = accent,
        secondary = secondary
    )

    CompositionLocalProvider(LocalQuietPingTheme provides state) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = QuietPingTypography,
            content = content
        )
    }
}

/**
 * Parse a `#RRGGBB` (or `#AARRGGBB`) hex string into a [Color], returning
 * [fallback] on any malformed input. Defensive: theme must never crash on a bad
 * persisted accent value.
 */
internal fun parseHexColor(hex: String, fallback: Color): Color {
    val cleaned = hex.trim().removePrefix("#")
    return try {
        when (cleaned.length) {
            6 -> Color(("FF$cleaned").toLong(16))
            8 -> Color(cleaned.toLong(16))
            else -> fallback
        }
    } catch (_: NumberFormatException) {
        fallback
    }
}
