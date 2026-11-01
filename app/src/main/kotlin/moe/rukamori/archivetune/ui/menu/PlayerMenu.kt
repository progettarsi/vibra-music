/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import app.vibra.ui.components.*
import coil3.compose.AsyncImage
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.playback.songShareUrl
import moe.rukamori.archivetune.playback.startSongDownload
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.rememberControlledSliderState
import moe.rukamori.archivetune.ui.player.rememberDeviceMusicVolumeController
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberLowDataModeActive
import moe.rukamori.archivetune.utils.shareLocalAudio

/** Legacy entry point for the bottom-sheet player; forwards to the callback-based menu. */
@Composable
fun PlayerMenu(
  mediaMetadata: MediaMetadata?,
  navController: NavController,
  playerBottomSheetState: BottomSheetState,
  isQueueTrigger: Boolean? = false,
  onPlayNextFromQueue: (() -> Unit)? = null,
  onRemoveFromQueue: (() -> Unit)? = null,
  onShowDetailsDialog: () -> Unit,
  onDismiss: () -> Unit,
) =
  PlayerMenu(
    mediaMetadata = mediaMetadata,
    navController = navController,
    onCollapsePlayer = playerBottomSheetState::collapseSoft,
    isQueueTrigger = isQueueTrigger,
    onPlayNextFromQueue = onPlayNextFromQueue,
    onRemoveFromQueue = onRemoveFromQueue,
    onShowDetailsDialog = onShowDetailsDialog,
    onDismiss = onDismiss,
  )

/**
 * Player overflow menu. [onCollapsePlayer] is invoked before navigating away from the player (e.g.
 * "Go to artist") so the destination is visible. Vibra passes its own player-sheet collapse here.
 */
@Composable
fun PlayerMenu(
  mediaMetadata: MediaMetadata?,
  navController: NavController,
  onCollapsePlayer: () -> Unit,
  isQueueTrigger: Boolean? = false,
  onPlayNextFromQueue: (() -> Unit)? = null,
  onRemoveFromQueue: (() -> Unit)? = null,
  onShowDetailsDialog: () -> Unit,
  onDismiss: () -> Unit,
) {
  mediaMetadata ?: return
  val context = LocalContext.current
  val database = LocalDatabase.current
  val downloadUtil = LocalDownloadUtil.current
  val playerConnection = LocalPlayerConnection.current ?: return
  val activityResultLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
  val librarySong by
    remember(database, mediaMetadata.id) { database.song(mediaMetadata.id) }
      .collectAsState(initial = null)
  val coroutineScope = rememberCoroutineScope()

  val download by
    remember(downloadUtil, mediaMetadata.id) { downloadUtil.getDownload(mediaMetadata.id) }
      .collectAsState(initial = null)

  val artists = remember(mediaMetadata.artists) { mediaMetadata.artists.filter { it.id != null } }

  // Artist separators for splitting artist names
  val artistSeparators = ",;/&"
  val playerDesignStyle by
    rememberEnumPreference(PlayerDesignStyleKey, defaultValue = PlayerDesignStyle.V4)
  val lowDataModeActive = rememberLowDataModeActive()

  val isLocalMedia =
    remember(librarySong?.song?.isLocal, mediaMetadata.id) {
      librarySong?.song?.isLocal == true || mediaMetadata.id.isLocalMediaId()
    }
  val castPlayerMenuAction = rememberCastPlayerMenuAction()

  // Split artists by configured separators
  data class SplitArtist(val name: String, val originalArtist: MediaMetadata.Artist?)

  val splitArtists =
    remember(artists, artistSeparators) {
      if (artistSeparators.isEmpty()) {
        artists.map { SplitArtist(it.name, it) }
      } else {
        val separatorRegex = "[${Regex.escape(artistSeparators)}]".toRegex()
        artists.flatMap { artist ->
          val parts = artist.name.split(separatorRegex).map { it.trim() }.filter { it.isNotEmpty() }
          if (parts.size > 1) {
            parts.mapIndexed { index, name -> SplitArtist(name, if (index == 0) artist else null) }
          } else {
            listOf(SplitArtist(artist.name, artist))
          }
        }
      }
    }

  var showSelectArtistDialog by rememberSaveable { mutableStateOf(false) }

  if (showSelectArtistDialog) {
    ListDialog(onDismiss = { showSelectArtistDialog = false }) {
      items(splitArtists.distinctBy { it.name }) { splitArtist ->
        ListItem(
          headlineContent = {
            Text(text = splitArtist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
          },
          leadingContent = {
            val thumbUrl = splitArtist.originalArtist?.thumbnailUrl
            if (thumbUrl.isNullOrBlank()) {
              Box(
                modifier =
                  Modifier.size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  painter = painterResource(R.drawable.music_note),
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(18.dp),
                )
              }
            } else {
              AsyncImage(
                model = thumbUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(CircleShape),
              )
            }
          },
          modifier =
            Modifier.fillMaxWidth().clickable {
              splitArtist.originalArtist?.let { artist ->
                navController.navigateToArtist(artist.id)
                showSelectArtistDialog = false
                onCollapsePlayer()
                onDismiss()
              }
            },
          colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
      }
    }
  }

  var showPitchTempoDialog by rememberSaveable { mutableStateOf(false) }

  if (showPitchTempoDialog) {
    TempoPitchDialog(onDismiss = { showPitchTempoDialog = false })
  }

  var showEqualizerDialog by rememberSaveable { mutableStateOf(false) }

  if (showEqualizerDialog) {
    EqualizerDialog(
      onDismiss = { showEqualizerDialog = false },
      openSystemEqualizer = {
        val intent =
          Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, playerConnection.localPlayer.audioSessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
          }
        if (intent.resolveActivity(context.packageManager) != null) {
          activityResultLauncher.launch(intent)
        }
      },
    )
  }

  val nowPlayingTitle =
    remember(mediaMetadata.title) {
      mediaMetadata.title.ifBlank { context.getString(R.string.no_title) }
    }

  val nowPlayingSubtitle =
    remember(mediaMetadata.artists) {
      mediaMetadata.artists.joinToString(separator = " • ") { it.name }
    }

  Column(
    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    MediaMenuHeader(
      nowPlayingTitle,
      nowPlayingSubtitle,
      mediaMetadata.thumbnailUrl,
      MediaMenuLayout.Compact,
      onPlay = null,
    )
    if (isQueueTrigger != true) PlayerVolumeControl()
    MenuCard {
      Column(Modifier.padding(vertical = 12.dp)) {
        castPlayerMenuAction?.let { MediaMenuActionRows(listOf(it)) }

        if (splitArtists.isNotEmpty() || mediaMetadata.album != null) {

          Column {
            if (splitArtists.isNotEmpty()) {
              MediaMenuNavigationRow(
                onArtist =
                  if (splitArtists.isNotEmpty()) {
                    {
                      if (splitArtists.size == 1 && splitArtists[0].originalArtist != null) {
                        onDismiss()
                        onCollapsePlayer()
                        navController.navigateToArtist(splitArtists[0].originalArtist!!.id)
                      } else {
                        showSelectArtistDialog = true
                      }
                    }
                  } else null,
                onAlbum =
                  if (mediaMetadata.album != null) {
                    {
                      onDismiss()
                      onCollapsePlayer()
                      navController.navigateToAlbum(mediaMetadata.album.id)
                    }
                  } else null,
              )
            }

            if (splitArtists.isNotEmpty() && mediaMetadata.album != null) {}

            if (mediaMetadata.album != null) {}
          }
        }
        if (!isLocalMedia) {

          when (download?.state) {
            Download.STATE_COMPLETED -> {
              MediaMenuRow(
                headlineContent = {
                  Text(
                    text = stringResource(R.string.remove_download),
                    color = MaterialTheme.colorScheme.error,
                  )
                },
                leadingContent = {
                  Icon(
                    painter = painterResource(R.drawable.offline),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                  )
                },
                modifier =
                  Modifier.clickable {
                    DownloadService.sendRemoveDownload(
                      context,
                      ExoDownloadService::class.java,
                      mediaMetadata.id,
                      false,
                    )
                  },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
              )
            }

            Download.STATE_QUEUED,
            Download.STATE_DOWNLOADING -> {
              MediaMenuRow(
                headlineContent = { Text(text = stringResource(R.string.downloading)) },
                leadingContent = { CircularWavyProgressIndicator(modifier = Modifier.size(24.dp)) },
                modifier =
                  Modifier.clickable {
                    DownloadService.sendRemoveDownload(
                      context,
                      ExoDownloadService::class.java,
                      mediaMetadata.id,
                      false,
                    )
                  },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
              )
            }

            else -> {
              MediaMenuRow(
                headlineContent = { Text(text = stringResource(R.string.action_download)) },
                leadingContent = {
                  Icon(painter = painterResource(R.drawable.download), contentDescription = null)
                },
                modifier =
                  Modifier.clickable { startSongDownload(context, database, mediaMetadata) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
              )
            }
          }
        }

        Column {
          if (isQueueTrigger == true && onPlayNextFromQueue != null) {
            MediaMenuRow(
              headlineContent = { Text(text = stringResource(R.string.play_next)) },
              leadingContent = {
                Icon(painter = painterResource(R.drawable.playlist_play), contentDescription = null)
              },
              modifier =
                Modifier.clickable {
                  onPlayNextFromQueue()
                  onDismiss()
                },
              colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
          }

          if (isQueueTrigger == true && onRemoveFromQueue != null) {
            MediaMenuRow(
              headlineContent = {
                Text(
                  text = stringResource(R.string.remove_from_queue),
                  color = MaterialTheme.colorScheme.error,
                )
              },
              leadingContent = {
                Icon(
                  painter = painterResource(R.drawable.delete),
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                )
              },
              modifier =
                Modifier.clickable {
                  onRemoveFromQueue()
                  onDismiss()
                },
              colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
          }

          MediaMenuRow(
            headlineContent = { Text(text = stringResource(R.string.details)) },
            leadingContent = {
              Icon(painter = painterResource(R.drawable.info), contentDescription = null)
            },
            modifier =
              Modifier.clickable {
                onShowDetailsDialog()
                onDismiss()
              },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          )

          if (isQueueTrigger != true) {

            MediaMenuRow(
              headlineContent = { Text(text = stringResource(R.string.equalizer)) },
              leadingContent = {
                Icon(painter = painterResource(R.drawable.equalizer), contentDescription = null)
              },
              modifier = Modifier.clickable { showEqualizerDialog = true },
              colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            MediaMenuRow(
              headlineContent = { Text(text = stringResource(R.string.vibra_menu_tempo_pitch)) },
              leadingContent = {
                Icon(painter = painterResource(R.drawable.speed), contentDescription = null)
              },
              supportingContent = {
                val playbackParameters by playerConnection.playbackParameters.collectAsState()
                Text(
                  text =
                    "x${formatMultiplier(
                                        playbackParameters.speed
                                    )} • x${formatMultiplier(playbackParameters.pitch)}",
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              },
              modifier = Modifier.clickable { showPitchTempoDialog = true },
              colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
          }
        }

        MediaMenuRow(
          headlineContent = { Text(stringResource(R.string.share)) },
          leadingContent = { Icon(painterResource(R.drawable.share), null) },
          modifier =
            Modifier.clickable {
              if (isLocalMedia)
                shareLocalAudio(context, mediaMetadata.id, librarySong?.format?.mimeType)
              else
                context.startActivity(
                  Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                      type = "text/plain"
                      putExtra(Intent.EXTRA_TEXT, songShareUrl(mediaMetadata.id))
                    },
                    null,
                  )
                )
              onDismiss()
            },
        )
      }
    }
  }
}

// Read volume in its own restart scope; dragging must not rebuild the action menu.
@Composable
private fun PlayerVolumeControl() {
  val controller = rememberDeviceMusicVolumeController()
  val change = remember(controller) { { value: Float -> controller.setVolumeFraction(value) } }
  PlayerVolumeCard(controller.volumeFraction, change)
}

@Composable
private fun PlayerVolumeCard(
  volume: Float,
  onVolumeChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val safeVolume = volume.coerceIn(0f, 1f)

  Surface(
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
          text = stringResource(R.string.volume),
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.weight(1f),
        )

        Text(
          text = "${(safeVolume * 100).roundToInt()}%",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
          Icon(
            painter = painterResource(R.drawable.volume_off),
            contentDescription = stringResource(R.string.minimum_volume),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )

          VolumeSliderL(
            value = safeVolume,
            onValueChange = onVolumeChange,
            modifier = Modifier.weight(1f),
          )

          Icon(
            painter = painterResource(R.drawable.volume_up),
            contentDescription = stringResource(R.string.maximum_volume),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun VolumeSliderL(
  value: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val safeValue = value.coerceIn(0f, 1f)
  var sliderValue by remember { mutableFloatStateOf(safeValue) }
  var isDragging by remember { mutableStateOf(false) }

  LaunchedEffect(safeValue) { if (!isDragging) sliderValue = safeValue }
  val sliderState = rememberControlledSliderState(value = sliderValue, valueRange = 0f..1f)

  Slider(
    state = sliderState,
    onValueChange = { updated ->
      isDragging = true
      val coerced = updated.coerceIn(0f, 1f)
      sliderState.value = coerced
      sliderValue = coerced
      onValueChange(coerced)
    },
    onValueChangeFinished = { isDragging = false },
    modifier = modifier.height(36.dp),
    thumb = {
      Box(
        modifier =
          Modifier.size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
      )
    },
    colors =
      SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
      ),
  )
}

@Composable
fun TempoPitchDialog(onDismiss: () -> Unit) {
  val playerConnection = LocalPlayerConnection.current ?: return
  val initialSpeed = remember { playerConnection.player.playbackParameters.speed }
  val initialPitch = remember { playerConnection.player.playbackParameters.pitch }

  var tempo by remember {
    mutableFloatStateOf(initialSpeed.safeCoerceIn(TempoMin, TempoMax, fallback = 1f))
  }

  var pitch by remember {
    mutableFloatStateOf(initialPitch.safeCoerceIn(PitchMin, PitchMax, fallback = 1f))
  }

  var pitchMode by rememberSaveable {
    mutableStateOf(if (isPitchSemitoneAligned(pitch)) PitchMode.Semitones else PitchMode.Multiplier)
  }

  val applyPlaybackParameters: (Float, Float) -> Unit = { speed, pitchMultiplier ->
    playerConnection.player.playbackParameters =
      PlaybackParameters(
        speed.coerceIn(TempoMin, TempoMax),
        pitchMultiplier.coerceIn(PitchMin, PitchMax),
      )
  }

  val metadata by playerConnection.mediaMetadata.collectAsState()
  val setTempo: (Float) -> Unit = {
    tempo = it.coerceIn(TempoMin, TempoMax).quantize(0.01f)
    if (playerConnection.player.playbackParameters.speed != tempo)
      applyPlaybackParameters(tempo, pitch)
  }
  val setPitch: (Float) -> Unit = {
    pitch = it.coerceIn(PitchMin, PitchMax)
    if (playerConnection.player.playbackParameters.pitch != pitch)
      applyPlaybackParameters(tempo, pitch)
  }
  VibraDialog(
    onDismissRequest = onDismiss,
    groupContent = false,
    contentPadding = PaddingValues(0.dp),
  ) {
    Column(
      Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      ControlSheetHeader(
        stringResource(R.string.vibra_menu_tempo_pitch),
        listOfNotNull(metadata?.title, metadata?.artists?.joinToString { it.name })
          .joinToString(" · "),
        R.drawable.speed,
      )
      MenuCard {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tempo), modifier = Modifier.weight(1f))
            Text(
              "×${formatMultiplier(tempo)}",
              color = MaterialTheme.colorScheme.primary,
              style = MaterialTheme.typography.titleLarge,
            )
          }
          Spacer(Modifier.height(6.dp))
          ControlSliderRow(
            multiplierToSlider(tempo),
            { setTempo(sliderToMultiplier(it)) },
            0f..1f,
            onDecrease = { setTempo(tempo - 0.01f) },
            onIncrease = { setTempo(tempo + 0.01f) },
            label = stringResource(R.string.tempo),
            valueLabel = "×${formatMultiplier(tempo)}",
          )
        }
      }
      MenuCard {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(stringResource(R.string.vibra_menu_pitch), modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
              ControlModeChoice(
                stringResource(R.string.vibra_menu_semitones),
                pitchMode == PitchMode.Semitones,
                true,
              ) {
                pitchMode = PitchMode.Semitones
              }
              ControlModeChoice("×", pitchMode == PitchMode.Multiplier, false) {
                pitchMode = PitchMode.Multiplier
              }
            }
            val semi = pitchToSemitones(pitch)
            Text(
              if (pitchMode == PitchMode.Semitones) "${if (semi > 0) "+" else ""}$semi"
              else "×${formatMultiplier(pitch)}",
              color = MaterialTheme.colorScheme.primary,
              style = MaterialTheme.typography.titleMedium,
            )
          }
          Spacer(Modifier.height(2.dp))
          if (pitchMode == PitchMode.Semitones) {
            val semi = pitchToSemitones(pitch)
            ControlSliderRow(
              semi.toFloat(),
              { setPitch(semitonesToPitch(it.roundToInt())) },
              -12f..12f,
              onDecrease = { setPitch(semitonesToPitch((semi - 1).coerceAtLeast(-12))) },
              onIncrease = { setPitch(semitonesToPitch((semi + 1).coerceAtMost(12))) },
              steps = 23,
              label = stringResource(R.string.vibra_menu_pitch),
              valueLabel = "$semi ${stringResource(R.string.vibra_menu_semitones)}",
            )
          } else
            ControlSliderRow(
              multiplierToSlider(pitch),
              { setPitch(sliderToMultiplier(it).quantize(0.01f)) },
              0f..1f,
              onDecrease = { setPitch((pitch - 0.01f).quantize(0.01f)) },
              onIncrease = { setPitch((pitch + 0.01f).quantize(0.01f)) },
              label = stringResource(R.string.vibra_menu_pitch),
              valueLabel = "×${formatMultiplier(pitch)}",
            )
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VibraDialogButton(
          onClick = {
            tempo = 1f
            pitch = 1f
            applyPlaybackParameters(tempo, pitch)
          },
          modifier = Modifier.weight(1f),
        ) {
          Text(stringResource(R.string.reset))
        }
        VibraDialogButton(onClick = onDismiss, primary = true, modifier = Modifier.weight(1f)) {
          Text(stringResource(R.string.vibra_menu_done))
        }
      }
    }
  }
}

private enum class PitchMode {
  Semitones,
  Multiplier,
}

private const val TempoMin = 0.25f
private const val TempoMax = 2f
private const val PitchMin = 0.25f
private const val PitchMax = 2f

private fun Float.safeCoerceIn(min: Float, max: Float, fallback: Float): Float {
  val safe = if (this.isFinite()) this else fallback
  return safe.coerceIn(min, max)
}

private fun Float.quantize(step: Float): Float {
  if (step <= 0f) return this
  return (round(this / step) * step).coerceAtLeast(0f)
}

private fun pitchToSemitones(pitch: Float): Int {
  val safePitch = pitch.safeCoerceIn(PitchMin, PitchMax, fallback = 1f).coerceAtLeast(0.0001f)
  return (12f * log2(safePitch)).roundToInt().coerceIn(-12, 12)
}

private fun semitonesToPitch(semitones: Int): Float =
  2f.pow(semitones.toFloat() / 12f).coerceIn(PitchMin, PitchMax)

internal fun isPitchSemitoneAligned(pitch: Float): Boolean {
  val safePitch = pitch.safeCoerceIn(PitchMin, PitchMax, fallback = 1f).coerceAtLeast(0.0001f)
  val semitones = (12f * log2(safePitch)).roundToInt()
  if (semitones !in -12..12) return false
  val reconstructed = 2f.pow(semitones.toFloat() / 12f)
  return abs(reconstructed - pitch) < 0.0015f
}

@Composable
private fun formatMultiplier(multiplier: Float): String =
  String.format(
    androidx.compose.ui.platform.LocalResources.current.configuration.locales[0],
    "%.2f",
    multiplier,
  )

private fun sliderToMultiplier(slider: Float): Float {
  val t = slider.coerceIn(0f, 1f)
  val y = (t - 0.5f) * 2f
  val curve = 2.2f
  val absY = abs(y).pow(curve)
  val shaped =
    when {
      y > 0f -> absY
      y < 0f -> -absY
      else -> 0f
    }
  val exponent = if (y < 0f) 2f * shaped else shaped
  return 2f.pow(exponent).coerceIn(TempoMin, TempoMax)
}

private fun multiplierToSlider(multiplier: Float): Float {
  val m = multiplier.coerceIn(TempoMin, TempoMax)
  val log = log2(m)
  val curve = 2.2f
  val shaped = if (m < 1f) (log / 2f) else log
  val absShaped = abs(shaped).pow(1f / curve)
  val y =
    when {
      shaped > 0f -> absShaped
      shaped < 0f -> -absShaped
      else -> 0f
    }
  return (0.5f + y / 2f).coerceIn(0f, 1f)
}
