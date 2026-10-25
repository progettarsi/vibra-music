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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
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
 */
object VibraEmotionalShapes {
    /** Polygon for a mood/genre label. */
    fun moodPolygon(label: String): RoundedPolygon {
        val text = label.lowercase()
        fun has(vararg words: String) = words.any { it in text }
        return when {
            has("energ", "workout", "allenament", "gym", "pump", "hype", "metal", "punk") -> MaterialShapes.Boom
            has("party", "festa", "rock") -> MaterialShapes.Burst
            has("relax", "rilass", "chill", "calm", "sleep", "dorm", "sonno", "ambient") -> MaterialShapes.Puffy
            has("focus", "concentr", "study", "studi") -> MaterialShapes.Gem
            has("classic", "classica", "jazz") -> MaterialShapes.Clover8Leaf
            has("romanc", "love", "amore", "romantic") -> MaterialShapes.Heart
            has("feel good", "buonumore", "happy", "felic", "summer", "estate") -> MaterialShapes.Sunny
            has("dance", "electr", "elettron", "edm", "house") -> MaterialShapes.VerySunny
            has("sad", "trist", "malincon", "heartbreak") -> MaterialShapes.Ghostish
            has("commut", "travel", "viagg", "drive", "guida") -> MaterialShapes.Arrow
            has("pop") -> MaterialShapes.Flower
            has("hip", "rap", "trap", "r&b", "soul") -> MaterialShapes.Cookie4Sided
            else -> MaterialShapes.Cookie9Sided
        }
    }

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
