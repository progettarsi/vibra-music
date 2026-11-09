/* Vibra (2026) - GPL-3.0. */
package app.vibra.playback

import androidx.media3.common.*
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup

/** Kotlin interface delegation does not forward Java default methods. Keep this bridge explicit. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal open class ForwardingPlayerListener(private val target: Player.Listener) :
    Player.Listener {
    override fun onEvents(player: Player, events: Player.Events) = target.onEvents(player, events)

    override fun onTimelineChanged(timeline: Timeline, reason: Int) =
        target.onTimelineChanged(timeline, reason)

    override fun onMediaItemTransition(item: MediaItem?, reason: Int) =
        target.onMediaItemTransition(item, reason)

    override fun onTracksChanged(tracks: Tracks) = target.onTracksChanged(tracks)

    override fun onMediaMetadataChanged(metadata: MediaMetadata) =
        target.onMediaMetadataChanged(metadata)

    override fun onPlaylistMetadataChanged(metadata: MediaMetadata) =
        target.onPlaylistMetadataChanged(metadata)

    override fun onIsLoadingChanged(loading: Boolean) = target.onIsLoadingChanged(loading)

    override fun onLoadingChanged(loading: Boolean) = target.onLoadingChanged(loading)

    override fun onAvailableCommandsChanged(commands: Player.Commands) =
        target.onAvailableCommandsChanged(commands)

    override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) =
        target.onTrackSelectionParametersChanged(parameters)

    override fun onPlayerStateChanged(ready: Boolean, state: Int) =
        target.onPlayerStateChanged(ready, state)

    override fun onPlaybackStateChanged(state: Int) = target.onPlaybackStateChanged(state)

    override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) =
        target.onPlayWhenReadyChanged(ready, reason)

    override fun onPlaybackSuppressionReasonChanged(reason: Int) =
        target.onPlaybackSuppressionReasonChanged(reason)

    override fun onIsPlayingChanged(playing: Boolean) = target.onIsPlayingChanged(playing)

    override fun onRepeatModeChanged(mode: Int) = target.onRepeatModeChanged(mode)

    override fun onShuffleModeEnabledChanged(enabled: Boolean) =
        target.onShuffleModeEnabledChanged(enabled)

    override fun onPlayerError(error: PlaybackException) = target.onPlayerError(error)

    override fun onPlayerErrorChanged(error: PlaybackException?) =
        target.onPlayerErrorChanged(error)

    override fun onPositionDiscontinuity(reason: Int) = target.onPositionDiscontinuity(reason)

    override fun onPositionDiscontinuity(
        old: Player.PositionInfo,
        new: Player.PositionInfo,
        reason: Int,
    ) = target.onPositionDiscontinuity(old, new, reason)

    override fun onPlaybackParametersChanged(parameters: PlaybackParameters) =
        target.onPlaybackParametersChanged(parameters)

    override fun onSeekBackIncrementChanged(increment: Long) =
        target.onSeekBackIncrementChanged(increment)

    override fun onSeekForwardIncrementChanged(increment: Long) =
        target.onSeekForwardIncrementChanged(increment)

    override fun onMaxSeekToPreviousPositionChanged(position: Long) =
        target.onMaxSeekToPreviousPositionChanged(position)

    override fun onAudioSessionIdChanged(id: Int) = target.onAudioSessionIdChanged(id)

    override fun onAudioAttributesChanged(attributes: AudioAttributes) =
        target.onAudioAttributesChanged(attributes)

    override fun onVolumeChanged(volume: Float) = target.onVolumeChanged(volume)

    override fun onSkipSilenceEnabledChanged(enabled: Boolean) =
        target.onSkipSilenceEnabledChanged(enabled)

    override fun onDeviceInfoChanged(info: DeviceInfo) = target.onDeviceInfoChanged(info)

    override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) =
        target.onDeviceVolumeChanged(volume, muted)

    override fun onVideoSizeChanged(size: VideoSize) = target.onVideoSizeChanged(size)

    override fun onSurfaceSizeChanged(width: Int, height: Int) =
        target.onSurfaceSizeChanged(width, height)

    override fun onRenderedFirstFrame() = target.onRenderedFirstFrame()

    override fun onCues(cues: List<Cue>) = target.onCues(cues)

    override fun onCues(cues: CueGroup) = target.onCues(cues)

    override fun onMetadata(metadata: Metadata) = target.onMetadata(metadata)
}
