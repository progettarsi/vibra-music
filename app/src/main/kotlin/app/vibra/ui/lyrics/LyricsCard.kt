/*
 * Vibra (2026) - an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune (github.com/rukamori/ArchiveTune).
 */

package app.vibra.ui.lyrics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.R
import androidx.compose.ui.graphics.Color as ComposeColor

internal const val MaxExportVerses = 4

@Immutable
internal data class LyricsCardContent(
    val title: String,
    val subtitle: String,
    val coverUrl: String?,
    val verses: List<String>,
)

internal enum class LyricsCardFormat(val height: Int) {
    Square(1080),
    Story(1920),
    ;

    val aspectRatio: Float get() = LyricsCardWidth.toFloat() / height
}

private const val LyricsCardWidth = 1080
private const val Margin = 72
private const val TextWidth = LyricsCardWidth - 2 * Margin

internal suspend fun loadLyricsCardCover(context: Context, url: String?): Bitmap? {
    if (url == null) return null
    return withTimeoutOrNull(2_500) {
        val request = ImageRequest.Builder(context).data(url).size(256).allowHardware(false).build()
        context.imageLoader.execute(request).image?.toBitmap()
    }
}

/** Returns null when the complete excerpt would need to be set below the readable type size. */
internal suspend fun renderLyricsCard(
    context: Context,
    content: LyricsCardContent,
    format: LyricsCardFormat,
    cover: Bitmap?,
    primary: ComposeColor,
    onPrimary: ComposeColor,
): Bitmap? = withContext(Dispatchers.Default) {
    val story = format == LyricsCardFormat.Story
    val height = format.height
    val base = primary.toArgb()
    val start = if (ColorUtils.calculateLuminance(base) < 0.45) {
        ColorUtils.blendARGB(base, Color.WHITE, 0.64f)
    } else {
        base
    }
    val end = ColorUtils.blendARGB(start, Color.rgb(89, 53, 171), 0.20f)
    val lowerCookie = ColorUtils.setAlphaComponent(Color.rgb(127, 95, 214), 41)
    val darkest = ColorUtils.compositeColors(lowerCookie, end)
    val themeInk = onPrimary.toArgb()
    val violetInk = Color.rgb(56, 30, 114)
    val ink = listOf(themeInk, violetInk).firstOrNull { candidate ->
        ColorUtils.calculateLuminance(candidate) < ColorUtils.calculateLuminance(darkest) &&
            ColorUtils.calculateContrast(candidate, darkest) >= 4.5
    } ?: Color.rgb(23, 19, 27)
    val baseFont = context.resources.getFont(R.font.vibra_google_sans_flex)
    val coverSize = if (story) 104 else 84
    val headerTop = if (story) 230 else Margin
    val footerInset = if (story) 300 else Margin
    val logoSize = if (story) 64 else 46
    val logoY = height - footerInset - logoSize
    val versesTop = headerTop + coverSize + 64
    val versesBottom = logoY - 36
    val preferredGap = if (story) 52 else 32
    var low = 44
    var high = if (story) 72 else 56
    var verses: List<StaticLayout>? = null
    var verseGap = preferredGap
    while (low <= high) {
        val size = (low + high) / 2
        val gap = (preferredGap * size / (if (story) 72 else 56)).coerceAtLeast(20)
        val layouts = content.verses.map { verse ->
            val rtl = TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(verse, 0, verse.length)
            cardText(
                verse, baseFont, size.toFloat(), ink, TextWidth,
                weight = if (rtl) 600 else 700,
                tracking = if (story) -0.3f else -0.2f,
                fallbackBold = rtl,
            )
        }
        if (layouts.sumOf { it.height } + gap * (layouts.size - 1) <= versesBottom - versesTop) {
            verses = layouts
            verseGap = gap
            low = size + 1
        } else {
            high = size - 1
        }
    }
    val fitted = verses ?: return@withContext null

    val bitmap = Bitmap.createBitmap(LyricsCardWidth, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    paint.shader = LinearGradient(
        0f, 0f, height * 0.26f, height.toFloat(),
        intArrayOf(start, ColorUtils.blendARGB(start, end, 0.44f), end),
        null,
        Shader.TileMode.CLAMP,
    )
    canvas.drawPaint(paint)
    paint.shader = null
    val upperRadius = if (story) 450f else 310f
    paint.color = ColorUtils.setAlphaComponent(Color.WHITE, 56)
    canvas.drawPath(cookie(LyricsCardWidth - upperRadius * 0.25f, upperRadius * 0.22f, upperRadius), paint)
    val lowerRadius = if (story) 380f else 260f
    paint.color = lowerCookie
    canvas.drawPath(cookie(lowerRadius * 0.16f, height - lowerRadius * 0.24f, lowerRadius), paint)

    val coverBounds = RectF(Margin.toFloat(), headerTop.toFloat(), (Margin + coverSize).toFloat(), (headerTop + coverSize).toFloat())
    canvas.save()
    val radius = if (story) 30f else 24f
    val clip = Path().apply { addRoundRect(coverBounds, radius, radius, Path.Direction.CW) }
    canvas.clipPath(clip)
    if (cover == null) {
        paint.color = ColorUtils.setAlphaComponent(ink, 46)
        canvas.drawRect(coverBounds, paint)
        val noteSize = coverSize / 2
        val noteInset = (coverSize - noteSize) / 2
        drawCardIcon(context, R.drawable.music_note, canvas, ink, Margin + noteInset, headerTop + noteInset, noteSize)
    } else {
        paint.alpha = 255
        val side = minOf(cover.width, cover.height)
        val left = (cover.width - side) / 2
        val top = (cover.height - side) / 2
        canvas.drawBitmap(cover, Rect(left, top, left + side, top + side), coverBounds, paint)
    }
    canvas.restore()

    val metaX = Margin + coverSize + 22
    val metaWidth = LyricsCardWidth - Margin - metaX
    val title = cardText(content.title, baseFont, if (story) 44f else 36f, ink, metaWidth, maxLines = 1, weight = 600)
    val subtitleAlpha = maxOf(204, ColorUtils.calculateMinimumAlpha(ink, darkest, 4.5f))
    val subtitle = cardText(
        content.subtitle, baseFont, if (story) 34f else 28f,
        ColorUtils.setAlphaComponent(ink, subtitleAlpha), metaWidth, maxLines = 1, weight = 500,
    )
    val subtitleHeight = subtitle.height.takeIf { content.subtitle.isNotBlank() } ?: 0
    val metaY = headerTop + (coverSize - title.height - subtitleHeight).coerceAtLeast(0) / 2
    canvas.drawLayout(title, metaX, metaY)
    if (subtitleHeight > 0) canvas.drawLayout(subtitle, metaX, metaY + title.height)

    val textHeight = fitted.sumOf { it.height } + verseGap * (fitted.size - 1)
    var verseY = if (story) versesTop else versesTop + (versesBottom - versesTop - textHeight) / 2
    for (layout in fitted) {
        canvas.drawLayout(layout, Margin, verseY)
        verseY += layout.height + verseGap
    }

    drawCardIcon(context, R.drawable.ic_vibra_logo, canvas, ink, Margin, logoY, logoSize)
    val signature = cardText("vibra", baseFont, if (story) 56f else 40f, ink, 240, weight = 950)
    val signatureY = minOf(logoY + (logoSize - signature.height) / 2, height - footerInset - signature.height)
    canvas.drawLayout(signature, Margin + logoSize + 16, signatureY)
    bitmap
}

private fun cardText(
    text: String,
    font: Typeface,
    size: Float,
    color: Int,
    width: Int,
    maxLines: Int = Int.MAX_VALUE,
    weight: Int,
    tracking: Float = 0f,
    fallbackBold: Boolean = false,
): StaticLayout {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font
        textSize = size
        this.color = color
        isFakeBoldText = fallbackBold
        setFontVariationSettings("'wght' $weight")
    }
    paint.textScaleX = 1f + tracking / size
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
        .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
        .setIncludePad(true)
        .apply {
            if (maxLines != Int.MAX_VALUE) setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
        }.build()
}

private fun Canvas.drawLayout(layout: StaticLayout, x: Int, y: Int) {
    save()
    translate(x.toFloat(), y.toFloat())
    layout.draw(this)
    restore()
}

private fun drawCardIcon(context: Context, icon: Int, canvas: Canvas, color: Int, x: Int, y: Int, size: Int) {
    context.getDrawable(icon)?.mutate()?.apply {
        setTint(color)
        setBounds(x, y, x + size, y + size)
        draw(canvas)
    }
}

private fun cookie(centerX: Float, centerY: Float, radius: Float): Path = Path().apply {
    for (step in 0 until 240) {
        val angle = step * 2 * PI / 240
        val distance = radius * (1 + 0.07 * cos(12 * angle))
        val x = centerX + (distance * cos(angle)).toFloat()
        val y = centerY + (distance * sin(angle)).toFloat()
        if (step == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
