/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.vibra.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import app.vibra.ui.motion.clickable
import app.vibra.ui.motion.expansionElement
import app.vibra.ui.motion.interactiveSurface
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import app.vibra.ui.motion.VibraFilledTonalButton as FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.vibra.ui.shell.avatarFlightSource
import app.vibra.ui.blocks.BlockSize
import app.vibra.ui.blocks.VibraBlock
import app.vibra.ui.blocks.blockColumnsFor
import app.vibra.ui.blocks.blockGrid
import app.vibra.ui.components.ShapeWallpaper
import app.vibra.ui.components.VibraArtwork
import app.vibra.ui.components.VibraBottomSheet
import app.vibra.ui.components.VibraCard
import app.vibra.ui.components.VibraGlyphButton
import app.vibra.ui.components.VibraPullToRefresh
import app.vibra.ui.components.rememberShapeTracingLoaderState
import app.vibra.ui.components.shapeProgress
import app.vibra.ui.components.shapeTracingLoader
import app.vibra.ui.haptics.rememberVibraHaptics
import app.vibra.ui.library.VibraLibraryViewModel
import app.vibra.ui.library.libraryBlocks
import app.vibra.ui.library.rememberLibraryData
import app.vibra.ui.library.rememberOpenLibraryList
import app.vibra.ui.motion.LiquidSpeed
import app.vibra.ui.motion.liquidPress
import app.vibra.ui.motion.rememberLiquidMorphShape
import app.vibra.ui.navigation.rememberVibraItemActions
import app.vibra.ui.shell.VibraRoutes
import app.vibra.ui.theme.Star
import app.vibra.ui.theme.VibraDimens
import app.vibra.ui.theme.VibraShapes
import kotlin.math.roundToInt
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.home.HomeAction
import moe.rukamori.archivetune.home.HomeScreenState
import moe.rukamori.archivetune.ui.screens.LOGIN_ROUTE
import moe.rukamori.archivetune.viewmodels.HomeViewModel

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  PROFILE (Figma "profile", 75:508) — the user's home inside the app
 * ─────────────────────────────────────────────────────────────────────────────────────
 *   ← back                                   (top-left, consistent with every screen)
 *   slowly drifting wallpaper of the user's shape
 *   avatar in the user's shape  — tap = edit mode (shape + main colour)
 *   name / sign in
 *   blocks:  ★ favourite artist (opens stats) · listening summary (opens stats)
 *            the library, ordered by habits (see library/VibraLibrary.kt)
 *  The dock's leading button becomes the settings gear on this screen (Figma "newdock").
 * ─────────────────────────────────────────────────────────────────────────────────────
 */
@Composable
fun VibraProfileScreen(
    navController: NavController,
    homeViewModel: HomeViewModel,
) {
    val actions = rememberVibraItemActions(navController)
    val style = rememberProfileStyle()
    val accountName by homeViewModel.accountName.collectAsStateWithLifecycle()
    val accountImage by homeViewModel.accountImageUrl.collectAsStateWithLifecycle()
    val pageData: VibraLibraryViewModel = hiltViewModel()
    val topArtists by pageData.weekTopArtist.collectAsStateWithLifecycle()
    val weekSongs by pageData.weekSongs.collectAsStateWithLifecycle()
    val library = rememberLibraryData(pageData)
    val openList = rememberOpenLibraryList(navController)
    val bottomPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val signedIn = accountName.isNotBlank()
    var editing by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val homeState by homeViewModel.screenState.collectAsStateWithLifecycle()
    val refreshing = (homeState as? HomeScreenState.Success)?.uiState?.isRefreshing == true

    val openStats = { navController.navigate(VibraRoutes.Stats) }
    val top = topArtists.firstOrNull()
    val weekMinutes = (weekSongs.sumOf { it.timeListened ?: 0L } / 60_000L).toInt()
    val favouriteTitle = stringResource(R.string.vibra_your_number_one)
    val statsTitle = stringResource(R.string.vibra_listening_summary)
    val blocks =
        buildList {
            add(
                VibraBlock(key = "favourite", relevance = 0.7f, minSize = BlockSize.Medium, maxSize = BlockSize.Medium) { _, modifier ->
                    FavouriteArtistBlock(
                        title = favouriteTitle,
                        artistName = top?.title,
                        artworkUrl = top?.thumbnailUrl,
                        listenedMs = top?.timeListened?.toLong() ?: 0L,
                        onClick = openStats,
                        modifier = modifier,
                    )
                },
            )
            if (weekSongs.isNotEmpty()) {
                add(VibraBlock(key = "summary_pair", relevance = 0.6f, minSize = BlockSize.Medium) { _, modifier ->
                    Row(modifier = modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(statsTitle, weekMinutes.toString(), pluralStringResource(R.plurals.vibra_minutes_this_week, weekMinutes), openStats, Modifier.weight(1f))
                        StatTile(stringResource(R.string.songs), weekSongs.size.toString(), pluralStringResource(R.plurals.vibra_songs_this_week, weekSongs.size), openStats, Modifier.weight(1f))
                    }
                })
            }
            addAll(libraryBlocks(library, actions, navController, openList, compact = true))
        }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ShapeWallpaper(
            polygon = style.shape.polygon,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            modifier = Modifier.fillMaxWidth().height(380.dp),
        )

        // Pull to refresh the account and library: the screen lowers and the avatar's own
        // outline shows the pull, then runs the shape loader while it refreshes.
        VibraPullToRefresh(
            isRefreshing = refreshing,
            onRefresh = { homeViewModel.onAction(HomeAction.Refresh) },
        ) { travel, pull ->
            BoxWithConstraints(travel.fillMaxSize()) {
                val columns = blockColumnsFor(maxWidth - VibraDimens.Gutter * 2)
                LazyColumn(
                    contentPadding =
                        PaddingValues(
                            start = VibraDimens.Gutter,
                            end = VibraDimens.Gutter,
                            top = topPadding + 56.dp,
                            bottom = bottomPadding + 16.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(VibraDimens.CardGap),
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "identity") {
                        ProfileIdentity(
                            name = accountName.ifBlank { stringResource(R.string.vibra_guest) },
                            avatarUrl = accountImage,
                            shape = style.shape,
                            signedIn = signedIn,
                            onEdit = { editing = true },
                            onSignIn = { navController.navigate(LOGIN_ROUTE) },
                            pull = { if (pull.isRefreshing) 0f else pull.pull() },
                            refreshing = pull.isRefreshing,
                        )
                    }
                    blockGrid(blocks = blocks, columns = columns)
                }
            }
        }

        app.vibra.ui.components.TopScrim()
        // Back, top-left (the profile is a destination like any other screen).
        VibraGlyphButton(
            icon = R.drawable.arrow_back,
            contentDescription = stringResource(R.string.vibra_back),
            onClick = { navController.navigateUp() },
            modifier = Modifier.padding(top = topPadding + 4.dp, start = 8.dp),
        )
    }

    if (editing) {
        ProfileEditSheet(style = style, avatarUrl = accountImage, onDismiss = { editing = false })
    }
}

/** Avatar in the user's shape (tap = edit), name and the sign-in action. */
@Composable
private fun ProfileIdentity(
    name: String,
    avatarUrl: String?,
    shape: ProfileShape,
    signedIn: Boolean,
    onEdit: () -> Unit,
    onSignIn: () -> Unit,
    pull: () -> Float,
    refreshing: Boolean,
) {
    val haptics = rememberVibraHaptics()
    val interaction = remember { MutableInteractionSource() }
    val editDescription = stringResource(R.string.vibra_edit_profile)
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomEnd) {
            ProfileAvatar(
                avatarUrl = avatarUrl,
                shape = shape,
                pull = pull,
                refreshing = refreshing,
                modifier =
                    Modifier
                        .size(220.dp)
                        // Flies into the dock's avatar button when the profile closes.
                        .avatarFlightSource(url = avatarUrl, shape = shape.polygon)
                        .liquidPress(interaction, pressedScale = 0.95f)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                            haptics.press()
                            onEdit()
                        }.semantics { contentDescription = editDescription },
            )
            Box(
                modifier =
                    Modifier
                        .padding(8.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.edit), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = VibraDimens.Gutter, vertical = 12.dp),
        )
        if (!signedIn) {
            FilledTonalButton(onClick = onSignIn) { Text(stringResource(R.string.login)) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Avatar clipped to the profile shape; the outline morphs when the shape changes. */
@Composable
private fun ProfileAvatar(
    avatarUrl: String?,
    shape: ProfileShape,
    modifier: Modifier = Modifier,
    pull: () -> Float = { 0f },
    refreshing: Boolean = false,
) {
    val outline = rememberLiquidMorphShape(target = shape.polygon, speed = LiquidSpeed.Slow)
    val loader = rememberShapeTracingLoaderState(active = refreshing)
    val ring = MaterialTheme.colorScheme.primary
    Box(
        modifier =
            modifier
                .shapeProgress(progress = pull, shape = outline, color = ring, strokeWidth = 4.dp)
                .shapeTracingLoader(state = loader, shape = outline, color = ring, strokeWidth = 4.dp, insetStroke = true)
                .clip(outline)
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            VibraArtwork(url = avatarUrl, shape = androidx.compose.ui.graphics.RectangleShape, sizePx = 720, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                painter = painterResource(R.drawable.person),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxSize(0.46f),
            )
        }
    }
}

/** Figma "favourites card": the week's most-listened artist in a star (it is special). */
@Composable
private fun FavouriteArtistBlock(
    title: String,
    artistName: String?,
    artworkUrl: String?,
    listenedMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VibraCard(modifier = modifier, onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (artworkUrl != null) {
                VibraArtwork(
                    url = artworkUrl,
                    shape = VibraShapes.Star,
                    sizePx = 320,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(104.dp),
                )
            } else {
                Box(
                    modifier = Modifier.size(104.dp).background(MaterialTheme.colorScheme.primaryContainer, VibraShapes.Star),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.star),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.star), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Text(
                    text = artistName ?: stringResource(R.string.vibra_no_favourite_yet),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (artistName != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.expansionElement(artistName, "title"),
                )
                if (listenedMs > 0) {
                    val minutes = (listenedMs / 60_000.0).roundToInt()
                    Text(
                        text = pluralStringResource(R.plurals.vibra_minutes_count, minutes, minutes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(painterResource(R.drawable.arrow_forward), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A big number with its caption (small block); opens the stats. */
@Composable
private fun StatTile(
    title: String,
    value: String,
    unit: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VibraCard(modifier = modifier.fillMaxHeight(), onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(min = 128.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Column {
                Text(text = value, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(text = unit, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Edit mode: live preview, shape picker and main-colour picker. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditSheet(
    style: ProfileStyleState,
    avatarUrl: String?,
    onDismiss: () -> Unit,
) {
    val haptics = rememberVibraHaptics()
    VibraBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = VibraDimens.Gutter, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.vibra_edit_profile), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            ProfileAvatar(avatarUrl = avatarUrl, shape = style.shape, modifier = Modifier.padding(vertical = 16.dp).size(140.dp))

            SectionLabel(stringResource(R.string.vibra_profile_shape))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ProfileShape.entries.forEach { option ->
                    val selected = option == style.shape
                    val label = stringResource(option.label)
                    Box(
                        modifier =
                            Modifier
                                .size(56.dp)
                                .interactiveSurface(
                                    shape = option.polygon.toShape(),
                                    // Outline, not a container tone: unselected shapes must stay readable
                                    // (3:1) on the sheet, not just the selected one.
                                    containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    role = Role.RadioButton,
                                    onClick = {
                                        haptics.tick()
                                        style.setShape(option)
                                    },
                                ).semantics {
                                    contentDescription = label
                                    this.selected = selected
                                },
                    )
                }
            }

            SectionLabel(stringResource(R.string.vibra_profile_colour))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            ) {
                ProfileColors.forEach { color ->
                    val selected = color == style.color
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .then(
                                    if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape) else Modifier,
                                ).padding(if (selected) 6.dp else 0.dp)
                                .interactiveSurface(
                                    shape = CircleShape,
                                    containerColor = color,
                                    role = Role.RadioButton,
                                    onClick = {
                                        haptics.toggle(on = true)
                                        style.setColor(color)
                                    },
                                ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
    )
}
