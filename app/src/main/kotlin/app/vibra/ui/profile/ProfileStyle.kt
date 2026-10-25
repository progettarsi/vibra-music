/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.vibra.ui.profile

import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.graphics.shapes.RoundedPolygon
import app.vibra.ui.theme.VibraEmotionalShapes
import app.vibra.ui.theme.VibraPalette
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.CustomThemeColorKey
import moe.rukamori.archivetune.utils.rememberPreference

/*
 * The profile's personal style, chosen in the profile's edit mode:
 *  - SHAPE: frames the avatar and repeats in the wallpaper behind it;
 *  - COLOUR: Vibra's main colour. It is written to the engine's theme seed
 *    (CustomThemeColorKey), so the whole app — not just the profile — adopts it.
 */

/** Avatar / wallpaper shapes offered in edit mode (M3 Expressive set + the star). */
enum class ProfileShape(
    @StringRes val label: Int,
) {
    Flower(R.string.vibra_shape_flower),
    Cookie(R.string.vibra_shape_cookie),
    Sunny(R.string.vibra_shape_sunny),
    Clover(R.string.vibra_shape_clover),
    Puffy(R.string.vibra_shape_puffy),
    Gem(R.string.vibra_shape_gem),
    Heart(R.string.vibra_shape_heart),
    Star(R.string.vibra_shape_star),
    Burst(R.string.vibra_shape_burst),
    ;

    val polygon: RoundedPolygon
        get() =
            when (this) {
                Flower -> MaterialShapes.Flower
                Cookie -> MaterialShapes.Cookie9Sided
                Sunny -> MaterialShapes.Sunny
                Clover -> MaterialShapes.Clover4Leaf
                Puffy -> MaterialShapes.Puffy
                Gem -> MaterialShapes.Gem
                Heart -> MaterialShapes.Heart
                Star -> VibraEmotionalShapes.Star
                Burst -> MaterialShapes.SoftBoom
            }
}

/** Main-colour choices: Vibra's own violet first, then a balanced set of seeds. */
val ProfileColors: List<Color> =
    listOf(
        VibraPalette.Seed,
        Color(0xFF2E6BD6), // blue
        Color(0xFF00897B), // teal
        Color(0xFF43A047), // green
        Color(0xFFF9A825), // amber
        Color(0xFFEF6C00), // orange
        Color(0xFFD81B60), // pink
        Color(0xFF8E24AA), // purple
    )

/** Persisted profile style with setters. */
class ProfileStyleState(
    val shape: ProfileShape,
    val color: Color,
    val setShape: (ProfileShape) -> Unit,
    val setColor: (Color) -> Unit,
)

@Composable
fun rememberProfileStyle(): ProfileStyleState {
    var shapeName by rememberPreference(ProfileShapeKey, defaultValue = ProfileShape.Flower.name)
    var themeColor by rememberPreference(CustomThemeColorKey, defaultValue = "default")
    val shape = ProfileShape.entries.firstOrNull { it.name == shapeName } ?: ProfileShape.Flower
    val color =
        themeColor
            .takeIf { it.startsWith("#") }
            ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
            ?: VibraPalette.Seed
    return ProfileStyleState(
        shape = shape,
        color = color,
        setShape = { shapeName = it.name },
        setColor = { picked ->
            // The Vibra seed means "the designed default scheme", stored as "default".
            themeColor = if (picked == VibraPalette.Seed) "default" else "#%06X".format(picked.toArgb() and 0xFFFFFF)
        },
    )
}

private val ProfileShapeKey = stringPreferencesKey("vibraProfileShape")
