/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.vibra.ui.profile

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.vibra.ui.components.RowBackground
import app.vibra.ui.components.RowSubtitleText
import app.vibra.ui.components.VibraArtwork
import app.vibra.ui.components.VibraCard
import app.vibra.ui.components.VibraGlyphButton
import app.vibra.ui.components.VibraRow
import app.vibra.ui.haptics.rememberVibraHaptics
import app.vibra.ui.library.VibraStatsViewModel
import app.vibra.ui.motion.Liquid
import app.vibra.ui.motion.LiquidSpeed
import app.vibra.ui.navigation.rememberVibraItemActions
import app.vibra.ui.theme.Star
import app.vibra.ui.theme.VibraDimens
import app.vibra.ui.theme.VibraShapes
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.utils.navigateToArtist

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  STATS — opened from the profile's favourite artist
 * ─────────────────────────────────────────────────────────────────────────────────────
 *   period:  Week · Month · Year · All time   (pill selector, content cross-fades)
 *   hero:    listening time, counted up when the period changes
 *   ★ N°1:   top artist in a star, then the next artists in their own shapes
 *   top songs (ranked rows, play counts) · top albums
 *  All numbers come from the engine's play events (the same data as ArchiveTune's stats
 *  screen); the profile's shape and colour drive the look, so stats feel like *yours*.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/** Selectable time ranges. */
enum class StatsPeriod(
    @StringRes val label: Int,
    val days: Long?,
) {
    Week(R.string.vibra_period_week, 7),
    Month(R.string.vibra_period_month, 30),
    Year(R.string.vibra_period_year, 365),
    AllTime(R.string.vibra_period_all, null),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VibraStatsScreen(navController: NavController) {
    val actions = rememberVibraItemActions(navController)
    val style = rememberProfileStyle()
    // Rows and the chosen period live in the page's ViewModel (see VibraLibraryData.kt).
    val stats: VibraStatsViewModel = hiltViewModel()
    val period by stats.period.collectAsStateWithLifecycle()
    val artists by stats.artists.collectAsStateWithLifecycle()
    val songs by stats.songs.collectAsStateWithLifecycle()
    val songStats by stats.songStats.collectAsStateWithLifecycle()
    val albums by stats.albums.collectAsStateWithLifecycle()
    val playCounts = remember(songStats) { songStats.associate { it.id to it.songCountListened } }
    val totalMinutes = (songStats.sumOf { it.timeListened ?: 0L } / 60_000L).toInt()
    val shownMinutes by animateIntAsState(totalMinutes, animationSpec = Liquid.effects(LiquidSpeed.Slow), label = "statsMinutes")
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            contentPadding = PaddingValues(start = VibraDimens.Gutter, end = VibraDimens.Gutter, top = topPadding + 64.dp, bottom = bottomPadding + 16.dp),
            verticalArrangement = Arrangement.spacedBy(VibraDimens.CardGap),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "period") { PeriodSelector(selected = period, onSelect = stats::select) }

            item(key = "hero") {
                VibraCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(84.dp).clip(style.shape.polygon.toShape()).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.vibra_schedule), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(34.dp))
                        }
                        Column(modifier = Modifier.padding(start = 18.dp)) {
                            Text(
                                text = shownMinutes.toString(),
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = pluralStringResource(R.plurals.vibra_minutes_listened, songStats.size, songStats.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (songStats.isEmpty()) {
                item(key = "empty") {
                    VibraCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.vibra_stats_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    }
                }
            }

            artists.firstOrNull()?.let { top ->
                item(key = "number_one") {
                    VibraCard(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { navController.navigateToArtist(top.id) }) {
                        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.size(180.dp).clip(VibraShapes.Star).background(MaterialTheme.colorScheme.primaryContainer)) {
                                VibraArtwork(url = top.thumbnailUrl, shape = RectangleShape, sizePx = 480, modifier = Modifier.fillMaxSize())
                            }
                            Row(modifier = Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(R.drawable.star), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text(
                                    text = stringResource(R.string.vibra_your_number_one),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                            Text(
                                text = top.title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = (top.timeListened ?: 0).div(60_000).let { pluralStringResource(R.plurals.vibra_minutes_total, it, it) },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (artists.size > 1) {
                item(key = "artists") {
                    VibraCard(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(stringResource(R.string.vibra_top_artists))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(artists.drop(1), key = { _, it -> it.id }) { index, artist ->
                                Column(
                                    modifier = Modifier.width(96.dp).clickable(role = Role.Button) { navController.navigateToArtist(artist.id) },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(contentAlignment = Alignment.TopStart) {
                                        VibraArtwork(
                                            url = artist.thumbnailUrl,
                                            shape = RankShapes[index % RankShapes.size].toShape(),
                                            sizePx = 240,
                                            modifier = Modifier.size(88.dp),
                                        )
                                        RankBadge(rank = index + 2)
                                    }
                                    Text(
                                        text = artist.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (songs.isNotEmpty()) {
                item(key = "songs") {
                    VibraCard(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(stringResource(R.string.vibra_top_songs))
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            songs.forEachIndexed { index, song ->
                                VibraRow(
                                    title = song.song.title,
                                    background = RowBackground.None,
                                    thumbnail = {
                                        Box {
                                            VibraArtwork(url = song.song.thumbnailUrl, shape = VibraShapes.RowThumbnail, sizePx = 160, modifier = Modifier.fillMaxSize())
                                        }
                                    },
                                    subtitle = { RowSubtitleText(song.artists.joinToString { it.name }) },
                                    trailing = {
                                        Text(
                                            text = "#${index + 1}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (index < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    bounceOnTap = true,
                                    interactionModifier =
                                        Modifier.combinedClickable(
                                            onClick = { actions.playSongs(songs, index) },
                                            onLongClick = { actions.showMenu(song) },
                                        ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                playCounts[song.id]?.let { count ->
                                    Text(
                                        text = pluralStringResource(R.plurals.vibra_play_count, count, count),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 72.dp, bottom = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (albums.isNotEmpty()) {
                item(key = "albums") {
                    VibraCard(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(stringResource(R.string.vibra_top_albums))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(albums, key = { it.id }) { album ->
                                Column(modifier = Modifier.width(132.dp).clickable(role = Role.Button) { actions.open(album) }) {
                                    VibraArtwork(url = album.thumbnailUrl, shape = VibraShapes.InnerArtwork, sizePx = 320, modifier = Modifier.size(132.dp))
                                    Text(
                                        text = album.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        app.vibra.ui.components.TopScrim()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = topPadding + 4.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VibraGlyphButton(
                icon = R.drawable.arrow_back,
                contentDescription = stringResource(R.string.vibra_back),
                onClick = { navController.navigateUp() },
            )
            Text(
                text = stringResource(R.string.vibra_your_stats),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** Pill selector; the selected pill fills with the primary colour (cross-faded). */
@Composable
private fun PeriodSelector(
    selected: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
) {
    val haptics = rememberVibraHaptics()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StatsPeriod.entries.forEach { option ->
            val active = option == selected
            val container by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                Liquid.effects(),
                label = "periodContainer",
            )
            val content by animateColorAsState(
                if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                Liquid.effects(),
                label = "periodContent",
            )
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(container)
                        .clickable(role = Role.Tab) {
                            haptics.tick()
                            onSelect(option)
                        },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(targetState = option, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "periodLabel") {
                    Text(stringResource(it.label), style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 18.dp, top = 16.dp),
    )
}

@Composable
private fun RankBadge(rank: Int) {
    Box(
        modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = rank.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
    }
}

/** Shapes for ranks 2…n: each artist gets its own expressive silhouette. */
private val RankShapes =
    listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Sunny,
        MaterialShapes.Gem,
        MaterialShapes.Puffy,
        MaterialShapes.Flower,
        MaterialShapes.SoftBurst,
    )
