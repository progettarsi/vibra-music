/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colour tokens extracted from the Vibra Figma file ("palette" frame 268:574 and the
 * `Schemes/…` variables bound on every screen).
 *
 * The design is authored against the Material 3 baseline *dark* scheme, so the four
 * palette swatches map 1:1 onto M3 roles:
 *  - Background         #141218  → background / surface
 *  - Primary            #D0BCFF  → primary (play buttons, progress, thumbnails)
 *  - Surface container  #211F26  → cards, dock pills, song rows
 *  - On surface         #E6E0E9  → primary text
 *
 * The remaining roles are the matching M3 baseline values so that every Material
 * component (dialogs, menus, settings) inherits a coherent palette.
 */
object VibraPalette {
    val Background = Color(0xFF141218)
    val Primary = Color(0xFFD0BCFF)
    val OnPrimary = Color(0xFF381E72)
    val SurfaceContainer = Color(0xFF211F26)
    val OnSurface = Color(0xFFE6E0E9)
    val OnSurfaceVariant = Color(0xFFCAC4D0)
    val Secondary = Color(0xFFCCC2DC)
    val SurfaceVariant = Color(0xFF49454F)
    val SecondaryContainer = Color(0xFF4A4458)
    val OnSecondaryContainer = Color(0xFFE8DEF8)

    /** Seed used whenever an algorithmic scheme must be generated (M3 baseline seed). */
    val Seed = Color(0xFF6750A4)
}

/** Exact Figma scheme (M3 baseline dark). */
val VibraDarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = VibraPalette.Primary,
        onPrimary = VibraPalette.OnPrimary,
        primaryContainer = Color(0xFF4F378B),
        onPrimaryContainer = Color(0xFFEADDFF),
        inversePrimary = Color(0xFF6750A4),
        secondary = VibraPalette.Secondary,
        onSecondary = Color(0xFF332D41),
        secondaryContainer = VibraPalette.SecondaryContainer,
        onSecondaryContainer = VibraPalette.OnSecondaryContainer,
        tertiary = Color(0xFFEFB8C8),
        onTertiary = Color(0xFF492532),
        tertiaryContainer = Color(0xFF633B48),
        onTertiaryContainer = Color(0xFFFFD8E4),
        background = VibraPalette.Background,
        onBackground = VibraPalette.OnSurface,
        surface = VibraPalette.Background,
        onSurface = VibraPalette.OnSurface,
        surfaceVariant = VibraPalette.SurfaceVariant,
        onSurfaceVariant = VibraPalette.OnSurfaceVariant,
        surfaceTint = VibraPalette.Primary,
        inverseSurface = VibraPalette.OnSurface,
        inverseOnSurface = Color(0xFF322F35),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
        outline = Color(0xFF938F99),
        outlineVariant = VibraPalette.SurfaceVariant,
        scrim = Color.Black,
        surfaceBright = Color(0xFF3B383E),
        surfaceDim = VibraPalette.Background,
        surfaceContainerLowest = Color(0xFF0F0D13),
        surfaceContainerLow = Color(0xFF1D1B20),
        surfaceContainer = VibraPalette.SurfaceContainer,
        surfaceContainerHigh = Color(0xFF2B2930),
        surfaceContainerHighest = Color(0xFF36343B),
    )

/**
 * Light counterpart. Figma ships the dark variant only; the component library's light
 * defaults (e.g. the dock's `#F3EDF7` surface-container fallback) are the M3 baseline
 * light scheme, so that is what light mode uses.
 */
val VibraLightColorScheme: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF6750A4),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEADDFF),
        onPrimaryContainer = Color(0xFF4F378B),
        inversePrimary = VibraPalette.Primary,
        secondary = Color(0xFF625B71),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE8DEF8),
        onSecondaryContainer = Color(0xFF4A4459),
        tertiary = Color(0xFF7D5260),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD8E4),
        onTertiaryContainer = Color(0xFF633B48),
        background = Color(0xFFFEF7FF),
        onBackground = Color(0xFF1D1B20),
        surface = Color(0xFFFEF7FF),
        onSurface = Color(0xFF1D1B20),
        surfaceVariant = Color(0xFFE7E0EC),
        onSurfaceVariant = Color(0xFF49454F),
        surfaceTint = Color(0xFF6750A4),
        inverseSurface = Color(0xFF322F35),
        inverseOnSurface = Color(0xFFF5EFF7),
        error = Color(0xFFB3261E),
        onError = Color.White,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF8C1D18),
        outline = Color(0xFF79747E),
        outlineVariant = Color(0xFFCAC4D0),
        scrim = Color.Black,
        surfaceBright = Color(0xFFFEF7FF),
        surfaceDim = Color(0xFFDED8E1),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF7F2FA),
        surfaceContainer = Color(0xFFF3EDF7),
        surfaceContainerHigh = Color(0xFFECE6F0),
        surfaceContainerHighest = Color(0xFFE6E0E9),
    )

fun vibraColorScheme(darkTheme: Boolean): ColorScheme = if (darkTheme) VibraDarkColorScheme else VibraLightColorScheme
