/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import app.vibra.ui.motion.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Timeline
import app.vibra.ui.components.RowDurationText
import app.vibra.ui.components.RowSubtitleText
import app.vibra.ui.components.SmallArtworkPx
import app.vibra.ui.components.VibraArtwork
import app.vibra.ui.components.VibraGlyphButton
import app.vibra.ui.components.VibraRow
import app.vibra.ui.haptics.rememberVibraHaptics
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.LiquidSheetState
import app.vibra.ui.motion.LiquidSpeed
import app.vibra.ui.motion.reorderWobble
import app.vibra.ui.theme.VibraDimens
import app.vibra.ui.theme.VibraShapes
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.move
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.isUserQueued
import moe.rukamori.archivetune.ui.player.QueueDragInfo
import moe.rukamori.archivetune.ui.player.commitQueueDrag
import moe.rukamori.archivetune.ui.player.queueDragDestinationFor
import moe.rukamori.archivetune.ui.player.queueItemKey
import moe.rukamori.archivetune.utils.makeTimeString
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * The queue (Figma "queueOpen" list, "Canzone" rows on surface-container cards).
 *
 *  - tap ............... jump to that track
 *  - long-press cover .. pick the row up and drag it (haptic on grab and on every slot
 *                        crossed); the other rows wobble lightly (Advanced setting)
 *  - swipe left ........ remove, with an Undo snackbar
 *  - pull down at top .. a further strong pull drags the whole sheet down and closes it
 *  - header ............ close (✕) only, no grabber (shuffle is in the player)
 *
 * Sections (Spotify's model, see the engine's UserQueue): after the current track come the
 * songs the user queued by hand ("Queued", played first, in the order chosen: Play next puts
 * a song at the top of it, Add to queue at its end), then the rest of what is playing
 * ("Next from <playlist>"). Each section starts with a small label, so the hand-made queue
 * reads as its own short list without boxing it in.
 *
 * Mutations go straight to the Media3 player through PlayerConnection, which also
 * persists the queue; the list is a projection of `queueWindows`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VibraQueuePanel(
    connection: PlayerConnection,
    nowPlaying: NowPlaying,
    queueSheet: LiquidSheetState,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val haptics = rememberVibraHaptics()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val queueWindows by connection.queueWindows.collectAsStateWithLifecycle()
    val currentIndex by connection.currentWindowIndex.collectAsStateWithLifecycle()
    val queueTitle by connection.queueTitle.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val pullToClose = remember(queueSheet) { PullToCloseConnection(queueSheet) }

    // Local mirror so a drag can reorder instantly; the player is updated on drop.
    val visibleWindows = remember { mutableStateListOf<Timeline.Window>() }
    var drag by remember { mutableStateOf<QueueDragInfo?>(null) }

    val reorderState =
        rememberReorderableLazyListState(lazyListState = listState) { from, to ->
            val fromIndex = visibleWindows.indexOfFirst { it.queueItemKey == from.key }
            val toIndex = visibleWindows.indexOfFirst { it.queueItemKey == to.key }
            if (fromIndex == -1 || toIndex == -1) return@rememberReorderableLazyListState
            val draggedUid = drag?.draggedItemUid ?: visibleWindows[fromIndex].uid
            visibleWindows.move(fromIndex, toIndex)
            drag = QueueDragInfo(draggedUid, queueDragDestinationFor(visibleWindows, toIndex))
            haptics.tick()
        }

    // Re-sync from the player whenever the timeline changes and no drag is in flight;
    // when a drag ends, commit it first (shared semantics with the legacy queue).
    LaunchedEffect(queueWindows, reorderState.isAnyItemDragging) {
        if (reorderState.isAnyItemDragging) return@LaunchedEffect
        drag?.let { completed ->
            drag = null
            if (connection.commitQueueDrag(queueWindows, completed)) {
                haptics.gestureEnd()
                return@LaunchedEffect
            }
        }
        Snapshot.withMutableSnapshot {
            visibleWindows.clear()
            visibleWindows.addAll(queueWindows)
        }
    }

    // Opening the queue brings the current track into view.
    LaunchedEffect(queueSheet.isExpanded) {
        if (queueSheet.isExpanded && currentIndex >= 0) {
            listState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
        }
    }

    // The queue is a sheet laid *over* the player (which recedes behind it), starting just
    // below the status bar so the receding player stays visible at the top edge.
    val sheetColor = MaterialTheme.colorScheme.surfaceContainerLow
    Box(
        modifier =
            modifier
                .statusBarsPadding()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(topStart = QueueSheetCorner, topEnd = QueueSheetCorner))
                .background(sheetColor),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            QueueHeader(onClose = queueSheet::collapse)
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .weight(1f)
                        .nestedScroll(pullToClose),
                contentPadding =
                    PaddingValues(
                        start = VibraDimens.Gutter,
                        end = VibraDimens.Gutter,
                        top = 8.dp,
                        // Leave room for the persistent bottom row + navigation bar.
                        bottom = VibraDimens.DockHeight + 96.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(VibraDimens.RowGap),
            ) {
                // Where the sections start, from the rows as shown (they follow a drag live).
                val currentUid = queueWindows.getOrNull(currentIndex)?.uid
                val currentRow = visibleWindows.indexOfFirst { it.uid == currentUid }
                val userEnd =
                    if (currentRow < 0) {
                        -1
                    } else {
                        var end = currentRow + 1
                        while (end < visibleWindows.size && visibleWindows[end].mediaItem.isUserQueued()) end++
                        end
                    }
                visibleWindows.forEachIndexed { index, window ->
                    if (currentRow >= 0 && index == currentRow + 1 && userEnd > currentRow + 1) {
                        item(key = SectionQueued, contentType = SectionQueued) { QueueSection(stringResource(R.string.vibra_queue_queued)) }
                    }
                    if (currentRow >= 0 && index == userEnd && userEnd < visibleWindows.size) {
                        item(key = SectionContext, contentType = SectionContext) {
                            QueueSection(queueTitle?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.vibra_queue_next_from, it) } ?: stringResource(R.string.vibra_queue_next))
                        }
                    }
                    item(key = window.queueItemKey, contentType = "row") {
                        ReorderableItem(reorderState, key = window.queueItemKey) { isDragging ->
                            QueueRow(
                                window = window,
                                isCurrent = window.uid == currentUid,
                                isDragging = isDragging,
                                modifier = Modifier.reorderWobble(active = reorderState.isAnyItemDragging && !isDragging, seed = index),
                                onClick = {
                                    haptics.press()
                                    connection.player.seekToDefaultPosition(window.firstPeriodIndex)
                                    connection.player.playWhenReady = true
                                },
                                onRemove = {
                                    val removal = connection.removeQueueItems(listOf(window)) ?: return@QueueRow
                                    haptics.confirm()
                                    scope.launch {
                                        val title = window.mediaItem.metadata?.title.orEmpty()
                                        val result =
                                            snackbarHostState.showSnackbar(
                                                message = resources.getString(R.string.vibra_removed_from_queue, title),
                                                actionLabel = resources.getString(R.string.undo),
                                                duration = SnackbarDuration.Short,
                                            )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            connection.restoreQueueItems(removal)
                                        } else {
                                            connection.discardQueueRemoval(removal)
                                        }
                                    }
                                },
                                dragHandleModifier =
                                    Modifier.longPressDraggableHandle(
                                        onDragStarted = { haptics.longPress() },
                                    ),
                            )
                        }
                    }
                }
            }
        }
        // Rows dissolve behind the persistent bottom row instead of colliding with it
        // (Figma queueOpen fades the list out towards the "From:" bar).
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(VibraDimens.DockHeight + 72.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to sheetColor.copy(alpha = 0.92f),
                            1f to sheetColor,
                        ),
                    ),
        )
        // The gradient stops at the navigation bar; fill the bar area itself.
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
                    .background(sheetColor),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = VibraDimens.DockHeight + 40.dp),
        )
    }
}

/** A section label in the queue ("Queued", "Next from …"): small, quiet, with air above it. */
@Composable
private fun QueueSection(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 14.dp, bottom = 2.dp).semantics { heading() },
    )
}

private const val SectionQueued = "section_queued"
private const val SectionContext = "section_context"

/** Close (✕), the queue's only close control (Figma queueOpen). */
@Composable
private fun QueueHeader(onClose: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // Clear of the sheet's rounded top corners.
                .padding(start = VibraDimens.Gutter - 6.dp, end = VibraDimens.Gutter - 6.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VibraGlyphButton(
            icon = R.drawable.close,
            contentDescription = stringResource(R.string.vibra_close_queue),
            onClick = onClose,
            iconSize = 30.dp,
        )
    }
}

/**
 * Lets the list hand an overscroll at its top to the sheet: once the list cannot scroll
 * further up, pulling down moves the whole queue down (with some resistance, so it takes
 * a deliberate pull), and releasing settles it open or closed by position and speed.
 * Scrolling back up first lifts the sheet back into place.
 */
private class PullToCloseConnection(
    private val sheet: LiquidSheetState,
) : NestedScrollConnection {
    private val pulled: Boolean get() = sheet.progress < 0.999f

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0f || !pulled) return Offset.Zero
        sheet.dragBy(available.y)
        return Offset(0f, available.y)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        sheet.dragBy(available.y * PullResistance)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!pulled) return Velocity.Zero
        sheet.settle(available.y)
        return available
    }
}

/** Share of the finger's travel that moves the sheet once the list is at its top. */
private const val PullResistance = 0.6f

private val QueueSheetCorner = 32.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(
    window: Timeline.Window,
    isCurrent: Boolean,
    isDragging: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
) {
    val metadata = window.mediaItem.metadata ?: return
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    onRemove()
                    true
                } else {
                    false
                }
            },
        )
    val reorderLabel = stringResource(R.string.vibra_reorder)
    val lift by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = Liquid.spatial(LiquidSpeed.Fast),
        label = "queueRowLift",
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !isCurrent,
        // Wobble and lift apply to the whole box, so the delete background never peeks out.
        modifier = modifier,
        backgroundContent = {
            // Only while swiping: a rocking or lifted row must not reveal it at its edges.
            if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(VibraShapes.Row)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(end = 24.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.delete),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        },
    ) {
        VibraRow(
            title = metadata.title,
            isActive = isCurrent || isDragging,
            thumbnail = {
                // The cover is the handle: hold it to pick the row up (no grip icon).
                VibraArtwork(
                    url = metadata.thumbnailUrl,
                    shape = VibraShapes.RowThumbnail,
                    sizePx = SmallArtworkPx,
                    modifier =
                        dragHandleModifier
                            .fillMaxSize()
                            .semantics { contentDescription = reorderLabel },
                )
            },
            subtitle = { RowSubtitleText(metadata.artists.joinToString { it.name }) },
            trailing = {
                if (metadata.duration > 0) RowDurationText(makeTimeString(metadata.duration * 1000L))
            },
            bounceOnTap = true,
            interactionModifier = Modifier.combinedClickable(onClick = onClick),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val scale = 1f + 0.03f * lift
                        scaleX = scale
                        scaleY = scale
                        shadowElevation = 12.dp.toPx() * lift
                        shape = VibraShapes.Row
                    },
        )
    }
}
