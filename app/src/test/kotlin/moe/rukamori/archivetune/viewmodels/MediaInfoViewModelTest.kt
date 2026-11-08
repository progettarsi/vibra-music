/*
 * Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package moe.rukamori.archivetune.viewmodels

import java.util.Locale
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.mediainfo.LocalMediaInfo
import moe.rukamori.archivetune.mediainfo.MediaInfoData
import moe.rukamori.archivetune.mediainfo.MediaInfoFormat
import moe.rukamori.archivetune.mediainfo.MediaInfoMetadata
import moe.rukamori.archivetune.mediainfo.MediaInfoState
import moe.rukamori.archivetune.mediainfo.MediaInfoStatistics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaInfoViewModelTest {
    @Test
    fun formatsMetricsForDisplayWithoutShorteningTheCopyValue() {
        val data = MediaInfoData(
            local = MediaInfoState.Empty,
            metadata = MediaInfoState.Success(MediaInfoMetadata(null, null, null, null, "2.5K")),
            statistics = MediaInfoState.Success(MediaInfoStatistics(12_400_123, 318_501, null)),
        )

        val italian = map(data, locale = Locale.ITALY).metrics
        assertEquals(listOf(R.string.views, R.string.likes, R.string.dislikes, R.string.subscribers), italian.map { it.labelRes })
        assertEquals("12,4 Mln", italian[0].displayValue)
        assertEquals("12.400.123", italian[0].value)
        assertEquals("318,5 K", italian[1].displayValue)
        assertEquals("318.501", italian[1].value)
        assertNull(italian[2].displayValue)
        assertNull(italian[2].value)
        assertEquals("2.5K", italian[3].displayValue)
        assertEquals("2.5K", italian[3].value)

        val english = map(data, locale = Locale.US).metrics.first()
        assertEquals("12.4 M", english.displayValue)
        assertEquals("12,400,123", english.value)
    }

    @Test
    fun exposesLocalTrackAndKeepsOnlineSectionsEmpty() {
        val data = MediaInfoData(
            local = MediaInfoState.Success(
                LocalMediaInfo(
                    "Track", "Artist", null,
                    isLocal = true,
                    format = MediaInfoFormat(-1, "audio/mpeg", "", 0, null, null, 0),
                    albumName = "Album",
                    durationSeconds = 123,
                ),
            ),
            metadata = MediaInfoState.Empty,
            statistics = MediaInfoState.Empty,
        )
        val result = map(data)
        assertTrue(result.isLocal)
        assertEquals(listOf(R.string.vibra_menu_duration, R.string.vibra_menu_album), result.overview.map { it.labelRes })
        assertEquals("Album", result.overview.last().value)
        assertEquals(MediaInfoState.Empty, result.statistics)
        assertEquals(MediaInfoState.Empty, result.descriptionState)
        val details = (result.technicalDetails as MediaInfoState.Success).data.map { it.labelRes }
        assertEquals(listOf(R.string.mime_type, R.string.media_id), details)
        assertFalse(map(data.copy(local = MediaInfoState.Empty)).isLocal)
    }

    @Test
    fun exposesOnlyPresentTechnicalFields() {
        val format = MediaInfoFormat(251, "", "opus", 0, null, null, 0)
        val data = MediaInfoData(
            local = MediaInfoState.Success(LocalMediaInfo(null, null, null, isLocal = false, format = format)),
            metadata = MediaInfoState.Empty,
            statistics = MediaInfoState.Empty,
        )

        val result = map(data)
        val details = (result.technicalDetails as MediaInfoState.Success).data.associate { it.labelRes to it.value }
        assertEquals(mapOf(R.string.media_info_itag to "251", R.string.codecs to "opus", R.string.media_id to "track-id"), details)
        val noFormat = MediaInfoState.Success(LocalMediaInfo(null, null, null, isLocal = false, format = null))
        val idOnly = (map(data.copy(local = noFormat)).technicalDetails as MediaInfoState.Success).data
        assertEquals(listOf(R.string.media_id), idOnly.map { it.labelRes })
        assertEquals(MediaInfoState.Empty, map(data.copy(local = MediaInfoState.Empty)).technicalDetails)
        assertEquals(MediaInfoState.Loading, map(data.copy(local = MediaInfoState.Loading)).technicalDetails)
    }

    private fun map(data: MediaInfoData, locale: Locale = Locale.US) =
        mapMediaInfo("track-id", data, locale) { error("No file size expected") }
}
