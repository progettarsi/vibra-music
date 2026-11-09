/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.components

import app.vibra.ui.motion.expansionElement
import app.vibra.ui.motion.ExpansionArtworkContent
import app.vibra.ui.shell.LocalPageFlight
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.resize

/**
 * Artwork image clipped to any (expressive) shape, with a designed fallback.
 *
 * When there is no URL — or loading fails — a themed gradient placeholder is shown
 * instead of a blank box, so missing artwork still looks intentional.
 *
 * @param sizePx requested decode size; the URL is resized server-side where supported
 *        (googleusercontent / ytimg), which keeps memory low in long lists.
 * @param placeholderSizePx decode size of a smaller copy of the same cover that is probably
 *        already in memory (e.g. [SmallArtworkPx] from the mini-player): it is shown at once
 *        while the large one loads, so a cover that grows never blinks empty.
 * @param containerColor colour shown inside the artwork shape while the image loads.
 * @param retainedPainter last visible cover for this item, also used while a replacement loads.
 * @param onLoaded called once the requested image (not the placeholder) is drawn.
 */
@Composable
fun VibraArtwork(
    url: String?,
    shape: Shape,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    sizePx: Int = 544,
    placeholderSizePx: Int? = null,
    @DrawableRes fallbackIcon: Int = R.drawable.music_note,
    containerColor: Color = Color.Transparent,
    primaryArtwork: Boolean = false,
    ytimgResizePolicy: YtimgResizePolicy = YtimgResizePolicy.AllowAnyAspect,
    retainedPainter: MutableState<Painter?>? = null,
    onLoaded: (() -> Unit)? = null,
) {
    val context = LocalPlatformContext.current
    val previous = retainedPainter?.value
    val transferPainter = LocalPageFlight.current?.let { remember(url, sizePx) { mutableStateOf<Painter?>(previous) } }
    var failed by remember(url, sizePx) { mutableStateOf(false) }
    if (retainedPainter != null && url.isNullOrBlank()) {
        LaunchedEffect(retainedPainter) { retainedPainter.value = null }
    }
    Box(
        modifier =
            modifier
                .expansionElement(
                    url, "artwork",
                    url?.let { ExpansionArtworkContent(it, shape, sizePx, containerColor, contentDescription, transferPainter) },
                    primary = primaryArtwork,
                )
                .clip(shape)
                .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank() || (failed && previous == null)) {
            ArtworkPlaceholder(fallbackIcon)
        } else {
            val model =
                remember(context, url, sizePx, placeholderSizePx, ytimgResizePolicy, retainedPainter) {
                    val resized = url.resize(sizePx, sizePx, ytimgResizePolicy = ytimgResizePolicy)
                    if (placeholderSizePx == null && retainedPainter == null) {
                        resized
                    } else {
                        ImageRequest
                            .Builder(context)
                            .data(resized)
                            .apply {
                                if (placeholderSizePx != null) {
                                    placeholderMemoryCacheKey(url.resize(placeholderSizePx, placeholderSizePx, ytimgResizePolicy = ytimgResizePolicy))
                                }
                                if (retainedPainter != null) crossfade(false)
                            }
                            .build()
                    }
                }
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                placeholder = previous,
                error = previous,
                onError = { failed = true },
                onSuccess = { result ->
                    transferPainter?.value = result.painter
                    if (retainedPainter != null) retainedPainter.value = result.painter
                    onLoaded?.invoke()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Decode size of cookie-sized covers (mini-player, dock). */
const val SmallArtworkPx = 160

/** Decode size of the full player's cover; prefetched while the mini-player shows a track. */
const val PlayerArtworkPx = 1080

/**
 * Gradient placeholder used when artwork is missing. It follows the theme palette
 * (primary → tertiary), so local playlists without covers never clash with the UI.
 */
@Composable
fun ArtworkPlaceholder(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(placeholderBrush()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            modifier = Modifier.fillMaxWidth(0.32f),
        )
    }
}

/** Convenience used by placeholders for list skeletons. */
@Composable
fun ArtworkSkeleton(
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

@Composable
private fun placeholderBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    return Brush.linearGradient(
        colors = listOf(scheme.primaryContainer, scheme.primary.copy(alpha = 0.85f), scheme.tertiaryContainer),
    )
}
