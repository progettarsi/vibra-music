/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import android.text.format.Formatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.mediainfo.MediaInfoData
import moe.rukamori.archivetune.mediainfo.MediaInfoDetail
import moe.rukamori.archivetune.mediainfo.MediaInfoEvent
import moe.rukamori.archivetune.mediainfo.MediaInfoMetric
import moe.rukamori.archivetune.mediainfo.MediaInfoState
import moe.rukamori.archivetune.mediainfo.MediaInfoUiModel
import moe.rukamori.archivetune.mediainfo.ObserveMediaInfoUseCase
import moe.rukamori.archivetune.mediainfo.asMediaInfoError

@HiltViewModel
class MediaInfoViewModel @Inject constructor(
    private val observeMediaInfo: ObserveMediaInfoUseCase,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val mutableState = MutableStateFlow<MediaInfoState<MediaInfoUiModel>>(MediaInfoState.Loading)
    val state = mutableState.asStateFlow()
    private val eventChannel = Channel<MediaInfoEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var loadJob: Job? = null
    private var currentVideoId: String? = null

    fun open(videoId: String) {
        if (currentVideoId == videoId && loadJob?.isActive == true) return
        loadJob?.cancel()
        currentVideoId = videoId
        mutableState.value = MediaInfoState.Loading
        if (videoId.isBlank()) {
            mutableState.value = MediaInfoState.Empty
            return
        }
        loadJob = viewModelScope.launch {
            observeMediaInfo(videoId)
                .map<MediaInfoData, MediaInfoState<MediaInfoUiModel>> { data ->
                    MediaInfoState.Success(
                        mapMediaInfo(
                            videoId,
                            data,
                            context.resources.configuration.locales[0],
                        ) { Formatter.formatShortFileSize(context, it) },
                    )
                }
                .catch { failure -> emit(failure.asMediaInfoError()) }
                .collect { mutableState.value = it }
        }
    }

    fun release(videoId: String) {
        if (currentVideoId != videoId) return
        loadJob?.cancel()
        loadJob = null
        currentVideoId = null
        mutableState.value = MediaInfoState.Loading
    }

    fun copy(value: String) {
        eventChannel.trySend(MediaInfoEvent.Copy(value))
    }
}

// Vibra: keep the display and clipboard representations separate.
internal fun mapMediaInfo(
    videoId: String,
    data: MediaInfoData,
    locale: Locale,
    formatFileSize: (Long) -> String,
): MediaInfoUiModel {
    val local = (data.local as? MediaInfoState.Success)?.data
    val metadata = (data.metadata as? MediaInfoState.Success)?.data
    val statistics = (data.statistics as? MediaInfoState.Success)?.data
    val format = local?.format
    val title = local?.title ?: metadata?.title ?: videoId
    val artists = local?.artists ?: metadata?.author
    val fileSize = format?.contentLength?.takeIf { it > 0 }?.let(formatFileSize)
    val bitrate = format?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} Kbps" }
    val technical = buildList {
        format?.let {
            it.itag.takeIf { value -> value > 0 }?.let { value ->
                add(MediaInfoDetail(R.string.media_info_itag, value.toString()))
            }
            it.mimeType.takeIf(String::isNotBlank)?.let { value -> add(MediaInfoDetail(R.string.mime_type, value)) }
            it.codecs.takeIf(String::isNotBlank)?.let { value -> add(MediaInfoDetail(R.string.codecs, value)) }
            bitrate?.let { value -> add(MediaInfoDetail(R.string.bitrate, value)) }
            it.sampleRate?.takeIf { value -> value > 0 }?.let { value -> add(MediaInfoDetail(R.string.sample_rate, "$value Hz")) }
            it.loudnessDb?.let { value -> add(MediaInfoDetail(R.string.loudness, "$value dB")) }
            fileSize?.let { value -> add(MediaInfoDetail(R.string.file_size, value)) }
        }
    }
    val technicalState: MediaInfoState<ImmutableList<MediaInfoDetail>> = when {
        data.local is MediaInfoState.Error -> data.local
        data.local is MediaInfoState.Loading -> MediaInfoState.Loading
        data.local is MediaInfoState.Empty -> MediaInfoState.Empty
        else -> MediaInfoState.Success(ImmutableList.copyOf(technical + MediaInfoDetail(R.string.media_id, videoId)))
    }
    return MediaInfoUiModel(
        videoId = videoId,
        title = title,
        subtitle = artists,
        artwork = local?.artwork ?: metadata?.artwork,
        isLocal = local?.isLocal == true,
        metadata = data.metadata,
        descriptionState = when (data.metadata) {
            is MediaInfoState.Success -> metadata?.description?.let { MediaInfoState.Success(it) } ?: MediaInfoState.Empty
            MediaInfoState.Loading -> MediaInfoState.Loading
            MediaInfoState.Empty -> MediaInfoState.Empty
            is MediaInfoState.Error -> data.metadata
        },
        technicalDetails = technicalState,
        statistics = data.statistics,
        overview = mediaInfoOverview(local?.albumName, local?.durationSeconds),
        metrics = ImmutableList.of(
            mediaInfoMetric(R.string.views, statistics?.views?.toLong(), locale),
            mediaInfoMetric(R.string.likes, statistics?.likes?.toLong(), locale),
            mediaInfoMetric(R.string.dislikes, statistics?.dislikes?.toLong(), locale),
            // YouTube may supply only a rounded subscriber count. Copy its original text.
            metadata?.subscribers?.takeIf(String::isNotBlank).let { value ->
                MediaInfoMetric(
                    R.string.subscribers,
                    value,
                    value?.toLongOrNull()?.let { compactMediaInfoCount(it, locale) } ?: value,
                )
            },
        ),
    )
}

private fun mediaInfoMetric(label: Int, value: Long?, locale: Locale): MediaInfoMetric =
    MediaInfoMetric(
        label,
        value?.let { NumberFormat.getIntegerInstance(locale).format(it) },
        value?.let { compactMediaInfoCount(it, locale) },
    )

private fun compactMediaInfoCount(value: Long, locale: Locale): String {
    if (value < 1_000) return NumberFormat.getIntegerInstance(locale).format(value)
    val italian = locale.language == Locale.ITALIAN.language
    val (divisor, suffix) = when {
        value >= 1_000_000_000 -> 1_000_000_000 to if (italian) "Mld" else "B"
        value >= 1_000_000 -> 1_000_000 to if (italian) "Mln" else "M"
        else -> 1_000 to "K"
    }
    val number = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 1
        roundingMode = RoundingMode.DOWN
    }
    return "${number.format(value.toDouble() / divisor)} $suffix"
}

internal fun mediaInfoOverview(album: String?, durationSeconds: Int?): ImmutableList<MediaInfoDetail> =
    ImmutableList.copyOf(buildList {
        durationSeconds?.let {
            add(MediaInfoDetail(R.string.vibra_menu_duration, makeTimeString(it * 1000L)))
        }
        album?.takeIf(String::isNotBlank)?.let {
            add(MediaInfoDetail(R.string.vibra_menu_album, it))
        }
    })
