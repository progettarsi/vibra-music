/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import androidx.media3.common.C
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.DownloaderFactory
import java.io.IOException

internal class ResumingDownloader(
    private val request: DownloadRequest,
    private val factory: DownloaderFactory,
    private val resetContent: (String) -> Unit,
    private val recover: (IOException) -> Boolean = { false },
) : Downloader {
    private val lock = Any()
    private var activeDownloader: Downloader? = null

    @Volatile
    private var cancelled = false

    override fun download(progressListener: Downloader.ProgressListener?) {
        runRecoveringDownload(
            attempt = {
                val downloader = factory.createDownloader(request)
                synchronized(lock) {
                    if (cancelled || Thread.currentThread().isInterrupted) throw InterruptedException()
                    activeDownloader = downloader
                }
                try { downloader.download(progressListener) }
                finally { synchronized(lock) { activeDownloader = null } }
            },
            reset = {
                resetContent(request.customCacheKey ?: request.id)
                progressListener?.onProgress(C.LENGTH_UNSET.toLong(), 0L, 0f)
            },
            recover = recover,
            isCancelled = { cancelled || Thread.currentThread().isInterrupted },
        )
    }

    override fun cancel() {
        val downloader = synchronized(lock) {
            cancelled = true
            activeDownloader
        }
        downloader?.cancel()
    }

    override fun remove() {
        factory.createDownloader(request).remove()
        resetContent(request.customCacheKey ?: request.id)
    }
}

internal class DownloadContentChangedException : IOException("Download audio content changed")

/** Bounded source recovery, after the failed transport has closed and released its cache locks. */
internal fun runRecoveringDownload(
    attempt: () -> Unit,
    reset: () -> Unit,
    recover: (IOException) -> Boolean,
    isCancelled: () -> Boolean,
) {
    var representationResets = 0
    while (true) {
        if (isCancelled()) throw InterruptedException()
        val failure = try { attempt(); return } catch (error: IOException) { error }
        if (isCancelled()) throw InterruptedException()
        if (failure is DownloadContentChangedException && representationResets++ == 0) {
            // One restart per source when a partial file has an incompatible representation.
        } else {
            if (!recover(failure)) throw failure
            representationResets = 0
        }
        reset()
    }
}
