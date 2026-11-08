/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.utils

import app.vibra.ui.components.VibraLoadingIndicator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.vibra.ui.components.MenuCard
import app.vibra.ui.components.MenuTokens
import app.vibra.ui.components.VibraArtwork
import app.vibra.ui.components.concentric
import app.vibra.ui.components.vibraDialogNaturalMaxHeight
import app.vibra.ui.haptics.rememberVibraHaptics
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.VibraFilledTonalButton
import app.vibra.ui.motion.clickable
import app.vibra.ui.theme.VibraDisplay
import kotlinx.coroutines.awaitCancellation
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.mediainfo.MediaInfoDetail
import moe.rukamori.archivetune.mediainfo.MediaInfoEvent
import moe.rukamori.archivetune.mediainfo.MediaInfoMetric
import moe.rukamori.archivetune.mediainfo.MediaInfoState
import moe.rukamori.archivetune.mediainfo.MediaInfoStatistics
import moe.rukamori.archivetune.mediainfo.MediaInfoUiModel
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.viewmodels.MediaInfoViewModel
import moe.rukamori.archivetune.viewmodels.mediaInfoOverview

@Composable
fun ShowMediaInfo(videoId: String) {
    val viewModel: MediaInfoViewModel = hiltViewModel()
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentState = state
    val knownTrack = remember(videoId, playerConnection) {
        playerConnection?.mediaMetadata?.value?.takeIf { it.id == videoId }
    }

    LaunchedEffect(videoId, viewModel) {
        viewModel.open(videoId)
        try {
            awaitCancellation()
        } finally {
            viewModel.release(videoId)
        }
    }
    LaunchedEffect(viewModel, lifecycleOwner, context) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is MediaInfoEvent.Copy -> copyToClipboard(context, event.value)
                }
            }
        }
    }
    key(videoId) {
        MediaInfoContent(
            videoId = videoId,
            state = if (currentState is MediaInfoState.Success && currentState.data.videoId != videoId) {
                MediaInfoState.Loading
            } else {
                currentState
            },
            knownTrack = knownTrack,
            onCopy = remember(viewModel) { viewModel::copy },
        )
    }
}

@Composable
private fun MediaInfoContent(
    videoId: String,
    state: MediaInfoState<MediaInfoUiModel>,
    knownTrack: MediaMetadata?,
    onCopy: (String) -> Unit,
) {
    val model = (state as? MediaInfoState.Success)?.data
    val title = stringResource(R.string.vibra_track_details)
    val haptics = rememberVibraHaptics()
    val copyValue: (String) -> Unit = { value ->
        onCopy(value)
        haptics.press()
    }
    val waiting = state is MediaInfoState.Loading ||
        (model?.metadata is MediaInfoState.Loading && model.statistics is MediaInfoState.Loading)
    val isLocal = model?.isLocal ?: videoId.isLocalMediaId()
    // BottomSheetPage already provides MenuTokens.Gutter on each side.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = vibraDialogNaturalMaxHeight().coerceAtLeast(0.dp))
            .semantics { paneTitle = title }
            .verticalScroll(rememberScrollState())
            .padding(bottom = MenuTokens.Gutter),
        verticalArrangement = Arrangement.spacedBy(MenuTokens.CompactGap),
    ) {
        if (videoId.isNotBlank()) {
            TrackCard(
                title = model?.title?.takeUnless { it == videoId } ?: knownTrack?.title ?: videoId,
                artist = model?.subtitle ?: knownTrack?.artists?.joinToString { it.name },
                artwork = model?.artwork ?: knownTrack?.thumbnailUrl,
                overview = model?.overview?.takeIf { it.isNotEmpty() }
                    ?: mediaInfoOverview(knownTrack?.album?.title, knownTrack?.duration?.takeIf { it > 0 }),
                isLocal = isLocal,
                onCopy = copyValue,
            )
        }
        when {
            waiting -> {
                if (!isLocal) NumbersCard(MediaInfoState.Loading, emptyList(), copyValue, skeleton = true)
                MenuCard { MediaInfoStatus(MediaInfoState.Loading) }
            }
            model == null -> MenuCard { MediaInfoStatus(state) }
            else -> {
                if (!isLocal) NumbersCard(model.statistics, model.metrics, copyValue)
                AudioCard(model.technicalDetails, copyValue)
                if (isLocal) LocalTrackNotice() else DescriptionCard(model.descriptionState)
                Text(
                    stringResource(R.string.vibra_media_info_copy_hint),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TrackCard(
    title: String,
    artist: String?,
    artwork: String?,
    overview: List<MediaInfoDetail>,
    isLocal: Boolean,
    onCopy: (String) -> Unit,
) {
    val inset = MenuTokens.CompactHeaderInset
    MenuCard {
        Column(Modifier.padding(inset), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VibraArtwork(
                    url = artwork,
                    shape = RoundedCornerShape(concentric(MenuTokens.CardRadius, inset)),
                    modifier = Modifier.size(88.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        title,
                        fontFamily = VibraDisplay,
                        fontSize = 22.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!artist.isNullOrBlank()) {
                        Text(
                            artist,
                            fontSize = 15.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (overview.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isLocal) {
                        InfoPill(
                            text = stringResource(R.string.vibra_media_info_on_phone),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            icon = R.drawable.storage,
                        )
                    }
                    overview.forEach { detail ->
                        val value = detail.value ?: return@forEach
                        val duration = detail.labelRes == R.string.vibra_menu_duration
                        val width = if (duration) Modifier else Modifier.weight(1f, fill = false)
                        InfoPill(
                            text = value,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            icon = if (duration) R.drawable.vibra_schedule else R.drawable.album,
                            modifier = width.copyValue(stringResource(detail.labelRes), value, onCopy),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: Int? = null,
    height: Dp = 36.dp,
) {
    Row(
        modifier.clip(CircleShape).background(color).heightIn(min = height).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
        Text(text, color = textColor, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NumbersCard(
    state: MediaInfoState<MediaInfoStatistics>,
    metrics: List<MediaInfoMetric>,
    onCopy: (String) -> Unit,
    skeleton: Boolean = false,
) {
    InfoCard(title = stringResource(R.string.vibra_media_info_numbers)) {
        when {
            skeleton -> InfoGrid(listOf(0, 1, 2, 3)) { _, modifier ->
                Column(
                    modifier.infoTile().padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SkeletonLine(64.dp, 26.dp)
                    SkeletonLine(88.dp, 13.dp)
                }
            }
            state is MediaInfoState.Success -> InfoGrid(metrics) { metric, modifier ->
                val label = stringResource(metric.labelRes)
                Column(
                    modifier.copyValue(label, metric.value, onCopy)
                        .infoTile()
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                ) {
                    Text(
                        metric.displayValue ?: "—",
                        fontSize = 26.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    InfoLabel(label, 13.sp)
                }
            }
            else -> MediaInfoStatus(state)
        }
    }
}

@Composable
private fun SkeletonLine(width: Dp, height: Dp) {
    Box(Modifier.size(width, height).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
}

@Composable
private fun AudioCard(
    state: MediaInfoState<List<MediaInfoDetail>>,
    onCopy: (String) -> Unit,
) {
    val fields = (state as? MediaInfoState.Success)?.data.orEmpty().mapNotNull { detail ->
        detail.value?.let { detail.labelRes to it }
    }.toMap()
    val itag = fields[R.string.media_info_itag]
    InfoCard(
        title = stringResource(R.string.vibra_media_info_audio),
        grouped = state !is MediaInfoState.Success || fields.size > 1,
        trailing = itag?.let { value ->
            {
                InfoPill(
                    text = "itag $value",
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    height = 24.dp,
                    modifier = Modifier.copyValue(stringResource(R.string.media_info_itag), value, onCopy),
                )
            }
        },
    ) {
        if (state is MediaInfoState.Success) {
            AudioSummary(fields, onCopy)
            val grid = listOf(R.string.loudness, R.string.file_size, R.string.mime_type)
                .mapNotNull { label -> fields[label]?.let { label to it } }
            InfoGrid(grid) { (labelRes, value), modifier ->
                val label = stringResource(labelRes)
                Column(modifier.copyValue(label, value, onCopy).infoTile().padding(12.dp)) {
                    InfoLabel(label, 13.sp)
                    Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            fields[R.string.media_id]?.let { MediaIdTile(it, onCopy) }
        } else {
            MediaInfoStatus(state)
        }
    }
}

@Composable
private fun AudioSummary(fields: Map<Int, String>, onCopy: (String) -> Unit) {
    val codec = fields[R.string.codecs]
    val bitrate = fields[R.string.bitrate]
    val sampleRate = fields[R.string.sample_rate]
    if (codec == null && bitrate == null && sampleRate == null) return

    Row(
        Modifier.fillMaxWidth().infoTile().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (codec != null) {
            val label = stringResource(R.string.codecs)
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .copyValue(label, codec, onCopy)
                    .padding(6.dp),
            ) {
                InfoLabel(label, 13.sp)
                Text(codec.uppercase(), fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(
            modifier = if (codec == null) Modifier.fillMaxWidth() else Modifier,
            horizontalAlignment = Alignment.End,
        ) {
            if (bitrate != null) {
                Text(
                    bitrate,
                    modifier = Modifier.clip(CircleShape)
                        .copyValue(stringResource(R.string.bitrate), bitrate, onCopy)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (sampleRate != null) {
                Text(
                    sampleRate,
                    modifier = Modifier.clip(CircleShape)
                        .copyValue(stringResource(R.string.sample_rate), sampleRate, onCopy)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MediaIdTile(value: String, onCopy: (String) -> Unit) {
    val label = stringResource(R.string.media_id)
    Row(
        Modifier.fillMaxWidth().copyValue(label, value, onCopy).infoTile().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            InfoLabel(label, 13.sp)
            Text(value, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        }
        Box(
            Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.copy),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun DescriptionCard(state: MediaInfoState<String>) {
    InfoCard(
        title = stringResource(R.string.description),
        grouped = state !is MediaInfoState.Success,
    ) {
        if (state is MediaInfoState.Success) {
            val description = state.data
            var expanded by rememberSaveable(description) { mutableStateOf(false) }
            var hasMore by remember(description) { mutableStateOf(false) }
            val fade = !expanded && hasMore
            Column(Modifier.fillMaxWidth().infoTile().padding(14.dp)) {
                Box(Modifier.fillMaxWidth().animateContentSize(Liquid.container())) {
                    SelectionContainer {
                        Text(
                            description,
                            modifier = if (fade) Modifier.descriptionFade() else Modifier,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            maxLines = if (expanded) Int.MAX_VALUE else 4,
                            onTextLayout = { if (!expanded) hasMore = it.hasVisualOverflow },
                        )
                    }
                }
                if (expanded || hasMore) {
                    Spacer(Modifier.height(8.dp))
                    VibraFilledTonalButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.heightIn(min = 40.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(stringResource(if (expanded) R.string.vibra_media_info_show_less else R.string.vibra_media_info_show_all))
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            painterResource(if (expanded) R.drawable.expand_less else R.drawable.expand_more),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        } else {
            MediaInfoStatus(state)
        }
    }
}

private fun Modifier.descriptionFade(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithCache {
        val mask = Brush.verticalGradient(
            colors = listOf(Color.Black, Color.Transparent),
            startY = (size.height - 22.sp.toPx()).coerceAtLeast(0f),
            endY = size.height,
        )
        onDrawWithContent {
            drawContent()
            drawRect(mask, blendMode = BlendMode.DstIn)
        }
    }

@Composable
private fun LocalTrackNotice() {
    MenuCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.vibra_media_info_local_title),
                modifier = Modifier.semantics { heading() },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            InfoLabel(stringResource(R.string.vibra_media_info_local_body), 14.sp)
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    grouped: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val heading = Modifier.semantics {
        heading()
        contentDescription = title
    }
    if (grouped) {
        MenuCard(modifier = heading) {
            Column(
                Modifier.fillMaxWidth().padding(MenuTokens.CardInset),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (trailing != null) {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) { trailing() }
                }
                content()
            }
        }
    } else {
        Column(heading.fillMaxWidth()) { content() }
    }
}

@Composable
private fun <T> InfoGrid(items: List<T>, tile: @Composable (T, Modifier) -> Unit) {
    items.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { item -> tile(item, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun InfoLabel(label: String, size: TextUnit) {
    Text(label, fontSize = size, lineHeight = (size.value + 4).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Modifier.infoTile(): Modifier =
    clip(MenuTokens.RowShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)

@Composable
private fun Modifier.copyValue(label: String, value: String?, onCopy: (String) -> Unit): Modifier {
    if (value == null) return this
    return clickable(role = Role.Button, onClickLabel = stringResource(R.string.copy)) { onCopy(value) }
        .clearAndSetSemantics { contentDescription = "$label: $value" }
}

@Composable
private fun MediaInfoStatus(state: MediaInfoState<*>) {
    val message = stringResource(
        when (state) {
            MediaInfoState.Loading -> R.string.please_wait
            is MediaInfoState.Error -> R.string.media_info_load_error
            MediaInfoState.Empty, is MediaInfoState.Success -> R.string.media_info_empty
        },
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MenuTokens.Gutter, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state is MediaInfoState.Loading) {
            VibraLoadingIndicator(modifier = Modifier.size(24.dp))
        }
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun copyToClipboard(
    context: Context,
    value: String,
) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(ClipData.newPlainText("text", value))
    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
}
