/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.vibra.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star

/*
 * Shapes that carry meaning (M3 Expressive shape set). A shape is chosen for what the
 * thing *feels* like, so the same mood always gets the same silhouette:
 *
 *   energy / workout / party / rock / metal ...... Boom, Burst      (spiky, explosive)
 *   relax / chill / sleep / calm ................. Puffy, Bun       (soft, rounded)
 *   focus / study / classical .................... Gem, Pentagon    (crisp, ordered)
 *   romance / love ............................... Heart
 *   feel good / happy / summer / dance ........... Sunny, VerySunny (radiant)
 *   sad / melancholy ............................. Ghostish         (drooping)
 *   commute / travel ............................. Arrow            (directional)
 *   pop .......................................... Flower           (playful scallops)
 *   favourite artist ............................. Star             (special)
 *   anything else ................................ Cookie9          (Vibra's default)
 *
 * Keywords cover English and Italian chip titles (YouTube localises mood chips).
 * Each family also has its colours ([MoodTone]); "anything else" has none and follows the theme.
 */

/** The colours of a mood: a gradient for its shape, an accent for its main action. */
@Immutable
class MoodTone(
    val bright: Color,
    val middle: Color,
    val deep: Color,
    val accent: Color,
    val onAccent: Color,
)

object VibraEmotionalShapes {
    private enum class Family { Energy, Party, Calm, Focus, Classical, Romance, FeelGood, Dance, Sad, Travel, Pop, Urban, Other }

    private fun familyOf(label: String): Family {
        val text = label.lowercase()
        fun has(vararg words: String) = words.any { it in text }
        return when {
            has("energ", "workout", "allenament", "gym", "pump", "hype", "metal", "punk") -> Family.Energy
            has("party", "festa", "rock") -> Family.Party
            has("relax", "rilass", "chill", "calm", "sleep", "dorm", "sonno", "ambient") -> Family.Calm
            has("focus", "concentr", "study", "studi") -> Family.Focus
            has("classic", "classica", "jazz") -> Family.Classical
            has("romanc", "love", "amore", "romantic") -> Family.Romance
            has("feel good", "buonumore", "happy", "felic", "summer", "estate") -> Family.FeelGood
            has("dance", "electr", "elettron", "edm", "house") -> Family.Dance
            has("sad", "trist", "malincon", "heartbreak") -> Family.Sad
            has("commut", "travel", "viagg", "drive", "guida") -> Family.Travel
            has("pop") -> Family.Pop
            has("hip", "rap", "trap", "r&b", "soul") -> Family.Urban
            else -> Family.Other
        }
    }

    /** Polygon for a mood/genre label. */
    fun moodPolygon(label: String): RoundedPolygon =
        when (familyOf(label)) {
            Family.Energy -> MaterialShapes.Boom
            Family.Party -> MaterialShapes.Burst
            Family.Calm -> MaterialShapes.Puffy
            Family.Focus -> MaterialShapes.Gem
            Family.Classical -> MaterialShapes.Clover8Leaf
            Family.Romance -> MaterialShapes.Heart
            Family.FeelGood -> MaterialShapes.Sunny
            Family.Dance -> MaterialShapes.VerySunny
            Family.Sad -> MaterialShapes.Ghostish
            Family.Travel -> MaterialShapes.Arrow
            Family.Pop -> MaterialShapes.Flower
            Family.Urban -> MaterialShapes.Cookie4Sided
            Family.Other -> MaterialShapes.Cookie9Sided
        }

    /** Colours for a mood/genre label; null when the label is in no family (use the theme). */
    fun moodTone(label: String): MoodTone? =
        when (familyOf(label)) {
            Family.Energy -> tone(0xFFFFA24C, 0xFFE2341B, 0xFF7A1747, 0xFFFFB59A, 0xFF4A1300)
            Family.Party -> tone(0xFFF06BFF, 0xFF9B2FD6, 0xFF3B1478, 0xFFF5B8FF, 0xFF3C0058)
            Family.Calm -> tone(0xFF62E0D3, 0xFF2B7A9E, 0xFF2E3A8C, 0xFF9EF0E6, 0xFF00332F)
            Family.Focus -> tone(0xFF8C9BFF, 0xFF4152C9, 0xFF1B2466, 0xFFBEC6FF, 0xFF0E1A5C)
            Family.Classical -> tone(0xFFE8C48A, 0xFF9C6B3F, 0xFF3F2A4E, 0xFFF3D9B0, 0xFF3A2608)
            Family.Romance -> tone(0xFFFF8FB1, 0xFFD6336C, 0xFF6E1840, 0xFFFFB1C8, 0xFF5A0F2C)
            Family.FeelGood -> tone(0xFFFFD54F, 0xFFFF9E2C, 0xFFD9531E, 0xFFFFE08A, 0xFF3D2E00)
            Family.Dance -> tone(0xFF5CE1FF, 0xFF3B6BE8, 0xFF5A1FB0, 0xFFA8ECFF, 0xFF00323F)
            Family.Sad -> tone(0xFF9FB6E3, 0xFF4F6597, 0xFF232F4D, 0xFFC4D4F5, 0xFF14233F)
            Family.Travel -> tone(0xFF7BD88F, 0xFF1F9E78, 0xFF0F4C5C, 0xFFAEEFC0, 0xFF003823)
            Family.Pop -> tone(0xFFFF8FD8, 0xFFB05CFF, 0xFF4B2BB3, 0xFFFFC2EA, 0xFF4A0E3C)
            Family.Urban -> tone(0xFFFFC861, 0xFFB8862B, 0xFF4A2E12, 0xFFFFDCA0, 0xFF3A2400)
            Family.Other -> null
        }

    /** A tone built on one colour, for categories in no family: YouTube's own stripe colour. */
    fun moodTone(stripe: Color): MoodTone {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(stripe.toArgb(), hsl)
        // A grey stays grey; any other colour is lifted to the saturation of the family tones.
        val saturation = if (hsl[1] < StripeNeutral) hsl[1] else hsl[1].coerceAtLeast(StripeMinSaturation)

        fun shade(
            hueShift: Float,
            lightness: Float,
        ) = Color(ColorUtils.HSLToColor(floatArrayOf((hsl[0] + hueShift + 360f) % 360f, saturation, lightness)))

        return MoodTone(
            bright = shade(0f, 0.66f),
            middle = shade(-14f, 0.42f),
            deep = shade(-28f, 0.26f),
            accent = shade(0f, 0.82f),
            onAccent = shade(0f, 0.15f),
        )
    }

    private const val StripeNeutral = 0.1f
    private const val StripeMinSaturation = 0.55f

    private fun tone(
        bright: Long,
        middle: Long,
        deep: Long,
        accent: Long,
        onAccent: Long,
    ) = MoodTone(Color(bright), Color(middle), Color(deep), Color(accent), Color(onAccent))

    /** True for categories Vibra deliberately does not surface (podcasts). */
    fun isExcludedCategory(label: String): Boolean = "podcast" in label.lowercase()

    /** Rounded five-pointed star, normalised to the unit square. */
    val Star: RoundedPolygon by lazy {
        RoundedPolygon
            .star(numVerticesPerRadius = 5, radius = 1f, innerRadius = 0.5f, rounding = CornerRounding(0.1f))
            .normalized()
    }
}

/** Composable [Shape] for a mood/genre label. */
@Composable
fun rememberMoodShape(label: String): Shape {
    val polygon = remember(label) { VibraEmotionalShapes.moodPolygon(label) }
    return polygon.toShape()
}

/** The favourite-artist star. */
val VibraShapes.Star: Shape @Composable get() = VibraEmotionalShapes.Star.toShape()
