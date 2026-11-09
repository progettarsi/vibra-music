/*
 * Vibra (2026) - an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune (github.com/rukamori/ArchiveTune).
 */

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package app.vibra.ui.lyrics

import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vibra.ui.components.ArtworkAccent
import app.vibra.ui.components.VibraBottomSheet
import app.vibra.ui.components.VibraDialogButton
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.VibraButton
import app.vibra.ui.motion.selectable
import app.vibra.ui.motion.VibraIconButton
import app.vibra.ui.motion.interactiveSurface
import app.vibra.ui.haptics.rememberVibraHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R

@Composable
internal fun LyricsSelectionBar(
    exportTint: Color,
    onCancel: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberVibraHaptics()
    val colors = MaterialTheme.colorScheme
    val controlColor = lerp(colors.inverseSurface, exportTint, 0.22f)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.widthIn(max = 172.dp).heightIn(min = 48.dp)
                .interactiveSurface(CircleShape, controlColor) {
                    haptics.press()
                    onPreview()
                }.padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(30.dp).background(lerp(exportTint, Color.Black, 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.vibra_share_nodes),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(17.dp),
                )
            }
            Text(
                stringResource(R.string.vibra_lyrics_share),
                color = colors.inverseOnSurface,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        VibraIconButton(
            onClick = {
                haptics.press()
                onCancel()
            },
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = controlColor,
                contentColor = colors.inverseOnSurface,
            ),
        ) {
            Icon(painterResource(R.drawable.close), stringResource(R.string.cancel), Modifier.size(20.dp))
        }
    }
}

private sealed interface CardPreview {
    data object Loading : CardPreview
    data object TooLong : CardPreview
    data object Failed : CardPreview
    data class Ready(val bitmap: Bitmap) : CardPreview
}

@Composable
internal fun LyricsExportDialog(
    content: LyricsCardContent,
    accent: ArtworkAccent,
    onDismiss: () -> Unit,
    saveLegacy: suspend (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val haptics = rememberVibraHaptics()
    // The card takes the cover's colour, like the player; the sheet itself stays in the theme.
    val primary = accent.color
    val onPrimary = accent.on
    var format by rememberSaveable { mutableStateOf(LyricsCardFormat.Square) }
    var cover by remember(content) { mutableStateOf<Bitmap?>(null) }
    var coverReady by remember(content) { mutableStateOf(false) }
    var preview by remember(content, format, primary, onPrimary) { mutableStateOf<CardPreview>(CardPreview.Loading) }
    var lastReady by remember(content, primary, onPrimary) { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember(content, format) { mutableStateOf<Int?>(null) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(content) {
        cover = try {
            loadLyricsCardCover(context, content.coverUrl)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
        coverReady = true
    }
    LaunchedEffect(content, format, coverReady, primary, onPrimary, attempt) {
        if (coverReady) {
            preview = CardPreview.Loading
            preview = try {
                renderLyricsCard(context, content, format, cover, primary, onPrimary)
                    ?.let {
                        lastReady = it
                        CardPreview.Ready(it)
                    } ?: CardPreview.TooLong
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                CardPreview.Failed
            }
        }
    }

    fun export(share: Boolean) {
        val bitmap = (preview as? CardPreview.Ready)?.bitmap ?: return
        if (busy) return
        busy = true
        status = null
        scope.launch {
            try {
                if (share) {
                    shareLyricsCard(context, writeLyricsCardToCache(context, bitmap))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveLyricsCardToGallery(context, bitmap)
                    status = R.string.vibra_lyrics_saved
                } else {
                    saveLegacy(bitmap)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                status = if (share) R.string.vibra_lyrics_share_failed else R.string.vibra_lyrics_save_failed
            } finally {
                busy = false
            }
        }
    }

    val card = preview
    val formatControls: @Composable () -> Unit = {
        LyricsFormatSelector(format, enabled = !busy, onSelect = { format = it })
    }
    val artwork: @Composable (Modifier) -> Unit = { modifier ->
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val targetWidth = minOf(maxWidth, maxHeight * format.aspectRatio, 300.dp)
            val width by animateDpAsState(targetWidth, Liquid.spatial(), label = "lyrics card width")
            val height by animateDpAsState(targetWidth / format.aspectRatio, Liquid.spatial(), label = "lyrics card height")
            val bitmap = when (card) {
                is CardPreview.Ready -> card.bitmap
                CardPreview.Loading -> lastReady
                else -> null
            }
            when {
                bitmap != null -> Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(
                        R.string.vibra_lyrics_card_preview,
                        content.verses.joinToString("\n"),
                    ),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width, height)
                        .shadow(14.dp, RoundedCornerShape(20.dp), clip = true),
                )
                card == CardPreview.Loading -> CircularProgressIndicator(Modifier.size(28.dp))
                card == CardPreview.TooLong -> Text(
                    stringResource(
                        if (format == LyricsCardFormat.Square) R.string.vibra_lyrics_card_too_long
                        else R.string.vibra_lyrics_portrait_too_long,
                    ),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
                card == CardPreview.Failed -> Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.vibra_lyrics_card_failed), textAlign = TextAlign.Center)
                    VibraDialogButton(onClick = { attempt++ }) {
                        Text(stringResource(R.string.vibra_lyrics_retry))
                    }
                }
            }
        }
    }
    val actions: @Composable () -> Unit = {
        Row(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val enabled = !busy && card is CardPreview.Ready
            LyricsExportAction(
                label = stringResource(R.string.vibra_lyrics_save),
                icon = R.drawable.download,
                onClick = {
                    haptics.press()
                    export(share = false)
                },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                primary = false,
            )
            LyricsExportAction(
                label = stringResource(R.string.vibra_lyrics_share),
                icon = R.drawable.vibra_share_nodes,
                onClick = {
                    haptics.press()
                    export(share = true)
                },
                modifier = Modifier.weight(1.3f),
                enabled = enabled,
                primary = true,
            )
        }
        val message = if (busy) R.string.vibra_lyrics_export_working else status
        if (message != null) {
            Text(
                stringResource(message),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val availableHeight = (windowHeight - 132.dp).coerceAtLeast(136.dp)
    VibraBottomSheet(
        openExpanded = true,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = { if (!busy) onDismiss() },
        sheetGesturesEnabled = !busy,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = !busy,
            shouldDismissOnClickOutside = !busy,
        ),
        containerColor = colors.surfaceContainer,
        sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        handleWidth = 36.dp,
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = if (windowHeight >= 550.dp) 28.dp else 12.dp)
                .heightIn(max = availableHeight),
        ) {
            val showTitle = maxHeight >= 280.dp
            if (maxWidth >= 460.dp || (maxWidth >= 420.dp && maxHeight < 340.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    artwork(Modifier.weight(1f).heightIn(max = 352.dp).fillMaxHeight())
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.share_lyrics), style = MaterialTheme.typography.headlineSmall)
                        formatControls()
                        actions()
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showTitle) {
                        Text(stringResource(R.string.share_lyrics), style = MaterialTheme.typography.headlineSmall)
                    }
                    formatControls()
                    artwork(Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(max = 352.dp).fillMaxHeight())
                    actions()
                }
            }
        }
    }
}

@Composable
private fun LyricsFormatSelector(
    format: LyricsCardFormat,
    enabled: Boolean,
    onSelect: (LyricsCardFormat) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberVibraHaptics()
    BoxWithConstraints(
        Modifier.widthIn(max = 284.dp).fillMaxWidth()
            .background(colors.surfaceContainerHighest, CircleShape)
            .padding(4.dp).selectableGroup(),
    ) {
        val segmentWidth = maxWidth / 2
        val offset by animateDpAsState(
            if (format == LyricsCardFormat.Square) 0.dp else segmentWidth,
            Liquid.spatial(),
            label = "lyrics format indicator",
        )
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier.offset(x = offset).width(segmentWidth).fillMaxHeight()
                    .background(colors.secondaryContainer, CircleShape),
            )
        }
        Row {
            for (option in LyricsCardFormat.entries) {
                val selected = format == option
                val story = option == LyricsCardFormat.Story
                val ink by animateColorAsState(
                    if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    Liquid.effects(),
                    label = "lyrics format label",
                )
                Row(
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) {
                            if (!selected) {
                                haptics.toggle(true)
                                onSelect(option)
                            }
                        }.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(if (story) 10.dp else 14.dp, 14.dp)
                        .border(1.5.dp, ink, RoundedCornerShape(2.dp)))
                    Text(
                        stringResource(if (story) R.string.vibra_lyrics_story_format else R.string.vibra_lyrics_square_format),
                        color = ink,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsExportAction(
    label: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    primary: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    VibraButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) colors.primary else colors.surfaceContainerHighest,
            contentColor = if (primary) colors.onPrimary else colors.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icon), contentDescription = null, Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Kept at screen level so a pending document picker survives the preview and screen layout. */
@Composable
internal fun rememberLegacyLyricsCardSaver(): suspend (Bitmap) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingFile by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val fileName = pendingFile ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                copyLyricsCardToDocument(context, fileName, uri)
                if (uri != null) Toast.makeText(context, R.string.vibra_lyrics_document_saved, Toast.LENGTH_SHORT).show()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                Toast.makeText(context, R.string.vibra_lyrics_save_failed, Toast.LENGTH_SHORT).show()
            } finally {
                pendingFile = null
            }
        }
    }
    return { bitmap ->
        check(pendingFile == null)
        val file = writeLyricsCardToCache(context, bitmap)
        pendingFile = file.name
        try {
            launcher.launch(lyricsCardFileName())
        } catch (exception: Exception) {
            pendingFile = null
            copyLyricsCardToDocument(context, file.name, null)
            throw exception
        }
    }
}
