/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.blocks

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import app.vibra.ui.motion.clickable
import app.vibra.ui.motion.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vibra.ui.components.ArtworkAtmosphere
import app.vibra.ui.components.RowBackground
import app.vibra.ui.components.RowDurationText
import app.vibra.ui.components.RowSubtitleText
import app.vibra.ui.components.ShelfEntry
import app.vibra.ui.components.VibraArtwork
import app.vibra.ui.components.VibraCard
import app.vibra.ui.components.VibraFilledIconButton
import app.vibra.ui.components.VibraPrimaryButton
import app.vibra.ui.components.VibraRow
import app.vibra.ui.components.entryTapBounce
import app.vibra.ui.haptics.rememberVibraHaptics
import app.vibra.ui.theme.VibraShapes
import app.vibra.ui.theme.rememberMoodShape
import moe.rukamori.archivetune.R

/*
 * The block catalogue (Figma "Vibra Components Reference"). Every block:
 *  - renders at the BlockSize the grid assigns (Small / Medium / Large);
 *  - fills the modifier it is given (small rows have a fixed, shared height);
 *  - degrades gracefully with little data (fewer tiles, never empty frames).
 */

/** A song row inside blocks. */
@Immutable
data class SongEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val duration: String?,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
)

/** A cookie beside the For You blob: a dedicated mix, started with one tap. */
@Immutable
data class MoodEntry(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

// ── ForYou ─────────────────────────────────────────────────────────────────────────

/**
 * Figma "ForYou": up to two mood cookies (each in the shape of its mood) next to the large
 * "✦ For You" blob. The blob shows the cover as a soft, dimmed colour field rather than a
 * sharp photo, so the label always reads and the block feels like atmosphere.
 */
@Composable
fun ForYouBlock(
    artworkUrl: String?,
    title: String,
    subtitle: String,
    moods: List<MoodEntry>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mosaicUrls: List<String> = emptyList(),
) {
    // Alone in its card (no mixes beside it) For You has the whole rectangle: it becomes a
    // mosaic of the songs it will play instead of a lone blob in an empty card.
    if (moods.isEmpty() && mosaicUrls.size >= MosaicMinCovers) {
        ForYouMosaic(urls = mosaicUrls, title = title, subtitle = subtitle, onClick = onClick, modifier = modifier)
        return
    }
    if (moods.size == 1) {
        BoxWithConstraints(modifier) {
            val stacked = maxWidth < 340.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
            val main: @Composable (Modifier) -> Unit = { area ->
                ForYouMosaic(mosaicUrls.ifEmpty { listOfNotNull(artworkUrl) }, title, subtitle, onClick, area, if (stacked) 180.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale else ForYouHeight, compact = true)
            }
            val mix: @Composable (Modifier) -> Unit = { area ->
                Box(area.clip(VibraShapes.Card).background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(role = Role.Button, onClick = moods.single().onClick).padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(moods.single().label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, textAlign = TextAlign.Center)
                }
            }
            if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                main(Modifier.fillMaxWidth()); mix(Modifier.fillMaxWidth().heightIn(min = 88.dp))
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                main(Modifier.weight(0.6f)); mix(Modifier.weight(0.4f).height(ForYouHeight))
            }
        }
        return
    }
    VibraCard(modifier = modifier.height(ForYouHeight)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (moods.isNotEmpty()) {
                Column(
                    modifier = Modifier.weight(0.47f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    moods.take(2).forEachIndexed { index, mood ->
                        MoodCookie(
                            mood = mood,
                            palette = if (index == 0) MoodPalette.Warm else MoodPalette.Cool,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val shape = VibraShapes.Sunny
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .clip(shape)
                            .clickable(role = Role.Button, onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    // A lighter blur than the player's backdrop: the cover stays recognisable.
                    ArtworkAtmosphere(
                        url = artworkUrl,
                        dim = 0.28f,
                        textureAlpha = 0.85f,
                        textureBlur = 26.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "✦ $title",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * For You on its own: the covers of the songs it will play, laid out as a slightly tilted
 * collage that fills the whole rounded card and is blurred into one soft field of their
 * colours — recognisable as "your music" without any single cover taking over. A dim and a
 * bottom gradient keep the white title readable on any covers.
 *
 * Static on purpose (no drifting): the blur is one GPU pass over a layer that never changes,
 * so the home scrolls as cheaply as with a single picture. Below Android 12 `blur` does
 * nothing, so the covers are dimmed more there instead.
 */
@Composable
private fun ForYouMosaic(
    urls: List<String>,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = ForYouHeight,
    compact: Boolean = false,
) {
    val haptics = rememberVibraHaptics()
    val blurSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    Box(
        modifier =
            modifier
                .height(height)
                .clip(VibraShapes.Card)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(role = Role.Button) {
                    haptics.press()
                    onClick()
                },
    ) {
        // The collage: two rows of three covers, oversized and tilted so no card edge or gap
        // shows, then blurred as one layer.
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = MosaicOverscan
                        scaleY = MosaicOverscan
                        rotationZ = MosaicTilt
                    }.blur(MosaicBlur, edgeTreatment = BlurredEdgeTreatment.Unbounded),
        ) {
            urls.take(MosaicCovers).chunked(MosaicCovers / 2).forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    row.forEach { url ->
                        VibraArtwork(
                            url = url,
                            shape = androidx.compose.ui.graphics.RectangleShape,
                            sizePx = 160,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (blurSupported) 0.22f else 0.5f))
                .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f))),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = if (compact) 88.dp else 20.dp),
        ) {
            Text(
                text = "✦ $title",
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 2,
            )
        }
        // The play cookie: the card is one big play button, this says so.
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(56.dp)
                    .clip(VibraShapes.Sunny)
                    .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

private enum class MoodPalette(
    val colors: List<Color>,
) {
    Warm(listOf(Color(0xFFE2541B), Color(0xFFB3261E), Color(0xFF7D2A6E))),
    Cool(listOf(Color(0xFF7B2CBF), Color(0xFF3F2A8C), Color(0xFF14213D))),
}

@Composable
private fun MoodCookie(
    mood: MoodEntry,
    palette: MoodPalette,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberVibraHaptics()
    val shape = rememberMoodShape(mood.label)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .clip(shape)
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(palette.colors))
                    .clickable(role = Role.Button) {
                        haptics.toggle(on = !mood.selected)
                        mood.onClick()
                    },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = mood.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (mood.selected) FontWeight.Bold else FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(16.dp),
            )
            if (mood.selected) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
    }
}

// ── songsCard ──────────────────────────────────────────────────────────────────────

/** Figma "songsCard": song rows; how many depends on the block size. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongsBlock(
    songs: List<SongEntry>,
    size: BlockSize,
    modifier: Modifier = Modifier,
    title: String? = null,
    onTitleClick: (() -> Unit)? = null,
) {
    val rows =
        when (size) {
            BlockSize.Large -> 6
            BlockSize.Medium -> 4
            BlockSize.Small -> 2
        }
    VibraCard(modifier = modifier) {
        title?.let { BlockTitle(text = it, onClick = onTitleClick) }
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = if (title == null) 12.dp else 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            songs.take(rows).forEach { song ->
                VibraRow(
                    title = song.title,
                    background = RowBackground.None,
                    thumbnail = {
                        VibraArtwork(url = song.thumbnailUrl, shape = VibraShapes.RowThumbnail, sizePx = 160, modifier = Modifier.fillMaxSize())
                    },
                    subtitle = { RowSubtitleText(song.subtitle) },
                    trailing = { if (size != BlockSize.Small) song.duration?.let { RowDurationText(it) } },
                    bounceOnTap = true,
                    interactionModifier = Modifier.combinedClickable(onClick = song.onClick, onLongClick = song.onLongClick),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── albumCollectionCard (mosaic) ───────────────────────────────────────────────────

/**
 * Figma "albumCollectionCard" generalised: one large cover plus smaller ones.
 *   Large  → title · big cover + 2×2 covers
 *   Medium → big cover + 2 stacked covers (the Figma card)
 *   Small  → title · 2×2 covers
 */
@Composable
fun MosaicBlock(
    title: String?,
    entries: List<ShelfEntry>,
    size: BlockSize,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
) {
    if (entries.isEmpty()) return
    VibraCard(modifier = modifier) {
        title?.let { BlockTitle(text = it, onClick = onTitleClick) }
        val inner = Modifier.fillMaxWidth().padding(10.dp)
        when {
            size == BlockSize.Small || entries.size < 3 ->
                CoverGrid(entries = entries.take(4), modifier = if (size == BlockSize.Small) inner.weight(1f) else inner)

            size == BlockSize.Medium ->
                Row(modifier = inner, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledCover(entries[0], Modifier.weight(1.9f), big = true)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        entries.drop(1).take(2).forEach { LabeledCover(it, Modifier.fillMaxWidth()) }
                    }
                }

            else ->
                Row(modifier = inner, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledCover(entries[0], Modifier.weight(1f), big = true)
                    CoverGrid(entries = entries.drop(1).take(4), modifier = Modifier.weight(1f))
                }
        }
    }
}

/** Cover with a centred title + subtitle (Figma coverCard text). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LabeledCover(
    entry: ShelfEntry,
    modifier: Modifier = Modifier,
    big: Boolean = false,
) {
    Column(
        modifier =
            modifier
                .entryTapBounce(entry)
                .clip(VibraShapes.InnerArtwork)
                .combinedClickable(onClick = entry.onClick, onLongClick = entry.onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VibraArtwork(
            url = entry.thumbnailUrl,
            shape = if (entry.circular) CircleShape else VibraShapes.InnerArtwork,
            contentDescription = entry.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Text(
            text = entry.title,
            style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
        )
        entry.subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * 2×2 grid of square covers (no labels). Cells are sized to fit both the width and, when
 * the block has a fixed height (small blocks), the height — so the grid never overflows.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverGrid(
    entries: List<ShelfEntry>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val gap = 6.dp
        if (entries.size == 1) {
            // A single item gets the whole grid instead of a lonely quarter.
            val entry = entries.single()
            val side = if (constraints.hasBoundedHeight) minOf(maxWidth, maxHeight) else maxWidth
            VibraArtwork(
                url = entry.thumbnailUrl,
                shape = if (entry.circular) CircleShape else VibraShapes.InnerArtwork,
                contentDescription = entry.title,
                modifier =
                    Modifier
                        .size(side)
                        .entryTapBounce(entry)
                        .clip(if (entry.circular) CircleShape else VibraShapes.InnerArtwork)
                        .combinedClickable(onClick = entry.onClick, onLongClick = entry.onLongClick),
            )
            return@BoxWithConstraints
        }
        val byWidth = (maxWidth - gap) / 2
        val cell = if (constraints.hasBoundedHeight) minOf(byWidth, (maxHeight - gap) / 2) else byWidth
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    pair.forEach { entry ->
                        VibraArtwork(
                            url = entry.thumbnailUrl,
                            shape = if (entry.circular) CircleShape else VibraShapes.RowThumbnail,
                            contentDescription = entry.title,
                            modifier =
                                Modifier
                                    .size(cell)
                                    .entryTapBounce(entry)
                                    .clip(if (entry.circular) CircleShape else VibraShapes.RowThumbnail)
                                    .combinedClickable(onClick = entry.onClick, onLongClick = entry.onLongClick),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.size(cell))
                }
            }
        }
    }
}

// ── spotlight ──────────────────────────────────────────────────────────────────────

/**
 * The feed's largest block: the first item's cover across the whole card with its title
 * written on it, the next few as a strip of covers below. Used now and then, so the feed
 * keeps a rhythm of sizes instead of running into a series of equal tiles.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotlightBlock(
    entries: List<ShelfEntry>,
    modifier: Modifier = Modifier,
    title: String? = null,
    onTitleClick: (() -> Unit)? = null,
) {
    val lead = entries.firstOrNull() ?: return
    VibraCard(modifier = modifier) {
        title?.let { BlockTitle(text = it, onClick = onTitleClick) }
        Box(
            modifier =
                Modifier
                    .padding(10.dp)
                    .fillMaxWidth()
                    .aspectRatio(SpotlightAspect)
                    .entryTapBounce(lead)
                    .clip(VibraShapes.InnerArtwork)
                    .combinedClickable(onClick = lead.onClick, onLongClick = lead.onLongClick),
        ) {
            VibraArtwork(url = lead.thumbnailUrl, shape = VibraShapes.InnerArtwork, contentDescription = lead.title, modifier = Modifier.fillMaxSize())
            // The title sits on the cover: a dark fade at the bottom keeps it readable.
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))),
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(
                    text = lead.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                lead.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val rest = entries.drop(1).take(SpotlightStrip)
        if (rest.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rest.forEach { entry ->
                    val shape = if (entry.circular) CircleShape else VibraShapes.RowThumbnail
                    VibraArtwork(
                        url = entry.thumbnailUrl,
                        shape = shape,
                        contentDescription = entry.title,
                        modifier =
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .entryTapBounce(entry)
                                .clip(shape)
                                .combinedClickable(onClick = entry.onClick, onLongClick = entry.onLongClick),
                    )
                }
                // Short strips keep the covers' size instead of stretching them.
                repeat(SpotlightStrip - rest.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ── coverCard ──────────────────────────────────────────────────────────────────────

/** Figma "coverCard" with play overlay: one item, artwork-first (small block). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CoverBlock(
    entry: ShelfEntry,
    onPlay: (() -> Unit)?,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    VibraCard(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp)
                    .weight(1f)
                    .fillMaxWidth()
                    .entryTapBounce(entry)
                    .clip(VibraShapes.InnerArtwork)
                    .combinedClickable(onClick = entry.onClick, onLongClick = entry.onLongClick),
            contentAlignment = Alignment.Center,
        ) {
            VibraArtwork(url = entry.thumbnailUrl, shape = VibraShapes.InnerArtwork, contentDescription = entry.title, modifier = Modifier.fillMaxSize())
            if (onPlay != null) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = stringResource(R.string.play),
                    tint = Color.White,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.28f))
                            .clickable(role = Role.Button, onClick = onPlay)
                            .padding(10.dp),
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            label?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = entry.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── playlistCard / playlistsCard ───────────────────────────────────────────────────

/**
 * Figma "playlistCard": soft-burst cover, title, "by …", Play + more.
 * Medium/Large → horizontal card; Small → the cover-over-title tile of "playlistsCard".
 */
@Composable
fun PlaylistBlock(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    size: BlockSize,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onPlay: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    shape: Shape = VibraShapes.SoftBurst,
) {
    VibraCard(modifier = modifier.clickable(role = Role.Button, onClick = onOpen)) {
        if (size == BlockSize.Small) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VibraArtwork(
                    url = thumbnailUrl,
                    shape = shape,
                    fallbackIcon = R.drawable.queue_music,
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().height(PlaylistRowHeight).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                VibraArtwork(
                    url = thumbnailUrl,
                    shape = shape,
                    fallbackIcon = R.drawable.queue_music,
                    modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                )
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.let {
                        Text(text = it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (onPlay != null) {
                            VibraPrimaryButton(
                                text = stringResource(R.string.play),
                                icon = R.drawable.play,
                                onClick = onPlay,
                                height = 40.dp,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        if (onMore != null) {
                            VibraFilledIconButton(
                                icon = R.drawable.more_horiz,
                                contentDescription = stringResource(R.string.vibra_more),
                                onClick = onMore,
                                size = 40.dp,
                                iconSize = 20.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Figma "playlistsCard": the top playlist highlighted in its own tile, the next ones as
 * covers beside it; the title row opens the full list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsBlock(
    title: String,
    entries: List<ShelfEntry>,
    size: BlockSize,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
) {
    if (entries.isEmpty()) return
    VibraCard(modifier = modifier) {
        BlockTitle(text = title, onClick = onSeeAll)
        if (size == BlockSize.Small) {
            CoverGrid(entries = entries.take(4), modifier = Modifier.fillMaxWidth().weight(1f).padding(10.dp))
            return@VibraCard
        }
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val first = entries.first()
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(VibraShapes.InnerArtwork)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .combinedClickable(onClick = first.onClick, onLongClick = first.onLongClick)
                        .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Covers stay whole squares: an expressive shape would cut parts of them off.
                VibraArtwork(
                    url = first.thumbnailUrl,
                    shape = VibraShapes.InnerArtwork,
                    fallbackIcon = R.drawable.queue_music,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                Text(
                    text = first.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            val rest = entries.drop(1).take(if (size == BlockSize.Large) 4 else 2)
            if (rest.isNotEmpty()) {
                Column(modifier = Modifier.weight(if (size == BlockSize.Large) 1f else 0.62f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rest.chunked(if (size == BlockSize.Large) 2 else 1).forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            line.forEach { entry ->
                                VibraArtwork(
                                    url = entry.thumbnailUrl,
                                    shape = VibraShapes.RowThumbnail,
                                    fallbackIcon = R.drawable.queue_music,
                                    contentDescription = entry.title,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .entryTapBounce(entry)
                                            .clip(VibraShapes.RowThumbnail)
                                            .combinedClickable(onClick = entry.onClick, onLongClick = entry.onLongClick),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── shared bits ────────────────────────────────────────────────────────────────────

/** Block heading; with [onClick] it shows a chevron and opens the full list. */
@Composable
fun BlockTitle(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Empty-state block content: an icon in an expressive shape and one line of guidance. */
@Composable
fun EmptyBlock(
    text: String,
    icon: Int,
    shape: Shape,
    modifier: Modifier = Modifier,
    action: (@Composable BoxScope.() -> Unit)? = null,
) {
    VibraCard(modifier = modifier) {
        EmptyBlockBody(text = text, icon = icon, shape = shape, action = action)
    }
}

@Composable
private fun EmptyBlockBody(
    text: String,
    icon: Int,
    shape: Shape,
    action: (@Composable BoxScope.() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(shape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(26.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) Box(content = action)
    }
}

private val ForYouHeight = 244.dp

/** Covers in the For You mosaic (two rows of three), and the fewest that make one. */
private const val MosaicCovers = 6
const val MosaicMinCovers = 3

/** How much the mosaic is enlarged and tilted so its edges never show inside the card. */
private const val MosaicOverscan = 1.35f
private const val MosaicTilt = -8f
private val MosaicBlur = 22.dp
private val PlaylistRowHeight = 137.dp

/** Spotlight cover: a little wider than tall, so the block is big without filling the screen. */
private const val SpotlightAspect = 1.25f

/** Covers in the spotlight's strip. */
private const val SpotlightStrip = 4
