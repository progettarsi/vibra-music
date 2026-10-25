/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Vibra: single-song download/share helpers extracted from PlayerMenu so the Vibra
 * player and the legacy menu share one implementation.
 */

package moe.rukamori.archivetune.playback

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.models.MediaMetadata

/** Public YouTube Music URL for a track id. */
fun songShareUrl(songId: String): String = "https://music.youtube.com/watch?v=$songId"

/**
 * Persists [mediaMetadata] (downloads reference the song row) and enqueues the download
 * in the foreground [ExoDownloadService].
 */
fun startSongDownload(
    context: Context,
    database: MusicDatabase,
    mediaMetadata: MediaMetadata,
) {
    database.transaction {
        insert(mediaMetadata)
    }
    val downloadRequest =
        DownloadRequest
            .Builder(mediaMetadata.id, mediaMetadata.id.toUri())
            .setCustomCacheKey(mediaMetadata.id)
            .setData(mediaMetadata.title.toByteArray())
            .build()
    DownloadService.sendAddDownload(context, ExoDownloadService::class.java, downloadRequest, false)
}

/** Removes a finished or queued download of [songId]. */
fun removeSongDownload(
    context: Context,
    songId: String,
) {
    DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, songId, false)
}

/** Opens the system share sheet with the track's public link. */
fun shareSong(
    context: Context,
    mediaMetadata: MediaMetadata,
) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, mediaMetadata.title)
            putExtra(Intent.EXTRA_TEXT, songShareUrl(mediaMetadata.id))
        }
    context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
