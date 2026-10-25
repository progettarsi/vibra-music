/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R

/*
 * Typography tokens — one family, Google Sans Flex, everywhere.
 *
 * The roles differ by weight, roundness and slant instead of by family:
 *   • display / headline ... VibraDisplay: rounded terminals (ROND), SemiBold‥Bold — big
 *                            titles, the player title, section headers;
 *   • title / body / label . VibraSans: plain terminals, Normal‥SemiBold — everything read;
 *   • italic ............... the slant axis (no separate file), for secondary voices such
 *                            as captions and "from …" lines (see VibraTextStyles).
 * Roboto stays bundled for the few inherited places that ask for it explicitly. All fonts
 * are variable files (OFL-1.1, licences in assets/licenses), so the UI renders identically
 * on OEM skins that replace the system font.
 */

/** Builds one static instance of a variable font file for a given weight/style. */
private fun variableFont(
    resId: Int,
    weight: FontWeight,
    style: FontStyle = FontStyle.Normal,
    vararg extraAxes: FontVariation.Setting,
) = Font(
    resId = resId,
    weight = weight,
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight), *extraAxes),
)

/**
 * Google Sans Flex — the expressive display family (player title "APNEA" in Figma).
 * `ROND` adds the soft, rounded terminals that match the cookie-shaped controls.
 */
val VibraDisplay: FontFamily =
    FontFamily(
        listOf(FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map {
            variableFont(R.font.vibra_google_sans_flex, it, FontStyle.Normal, FontVariation.Setting("ROND", 40f))
        },
    )

private val SansWeights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

/** Google Sans Flex for reading text; italics come from its slant axis. */
val VibraSans: FontFamily =
    FontFamily(
        SansWeights.map { variableFont(R.font.vibra_google_sans_flex, it) } +
            SansWeights.map {
                variableFont(R.font.vibra_google_sans_flex, it, FontStyle.Italic, FontVariation.Setting("slnt", -10f))
            },
    )

/**
 * M3 type scale: display/headline roles in [VibraDisplay], the rest in [fontFamily]
 * (Google Sans Flex unless the caller asks otherwise).
 */
fun vibraTypography(fontFamily: FontFamily = VibraSans): Typography {
    fun style(
        size: Int,
        lineHeight: Int,
        weight: FontWeight,
        tracking: Float = 0f,
        family: FontFamily = fontFamily,
    ) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.sp,
    )

    return Typography(
        displayLarge = style(57, 64, FontWeight.SemiBold, -0.25f, VibraDisplay),
        displayMedium = style(45, 52, FontWeight.SemiBold, family = VibraDisplay),
        displaySmall = style(36, 44, FontWeight.SemiBold, family = VibraDisplay),
        headlineLarge = style(32, 40, FontWeight.SemiBold, family = VibraDisplay),
        headlineMedium = style(28, 36, FontWeight.SemiBold, family = VibraDisplay),
        headlineSmall = style(24, 32, FontWeight.SemiBold, family = VibraDisplay),
        titleLarge = style(22, 28, FontWeight.Medium),
        titleMedium = style(16, 24, FontWeight.Medium, 0.15f),
        titleSmall = style(14, 20, FontWeight.Medium, 0.1f),
        bodyLarge = style(16, 24, FontWeight.Normal, 0.5f),
        bodyMedium = style(14, 20, FontWeight.Normal, 0.25f),
        bodySmall = style(12, 16, FontWeight.Normal, 0.4f),
        labelLarge = style(14, 20, FontWeight.Medium, 0.1f),
        labelMedium = style(12, 16, FontWeight.Medium, 0.5f),
        labelSmall = style(11, 16, FontWeight.Medium, 0.5f),
    )
}

/** Extra roles the M3 scale does not cover. */
object VibraTextStyles {
    /**
     * Now-playing title: Google Sans Flex Bold, rounded. Sized like Apple Music / Koda so a
     * long title still leaves the cover room (it was 45 sp, which dominated the screen).
     */
    val PlayerTitle =
        TextStyle(
            fontFamily = VibraDisplay,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.2).sp,
        )

    /** Artists under the player title. */
    val PlayerArtist =
        TextStyle(
            fontFamily = VibraSans,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp,
            lineHeight = 24.sp,
        )

    /** Secondary voice ("From: …", captions): the italic of the reading family. */
    val Caption =
        TextStyle(
            fontFamily = VibraSans,
            fontWeight = FontWeight.Medium,
            fontStyle = FontStyle.Italic,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

    /** Large lyric lines share the expressive display family. */
    val Lyrics =
        TextStyle(
            fontFamily = VibraDisplay,
            fontWeight = FontWeight.Bold,
        )
}
