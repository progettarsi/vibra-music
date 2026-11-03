/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 *
 * The "ask sources in order, verify, cache misses too" shape follows BitChord's
 * CanvasRepository.kt (GPL-3.0); concurrency and caching are Vibra's own.
 */

package app.vibra.canvas

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.isLocalMediaId

/** One catalogue. [find] returns null for "no clip here" and throws for "could not ask". */
interface CanvasProvider {
    val source: CanvasSource

    suspend fun find(query: CanvasQuery): CanvasCandidate?
}

/** A settled answer from the cache; [artwork] is null for a remembered miss. */
class CanvasCached(val artwork: CanvasArtwork?)

/**
 * Finds the clip that belongs behind a track's cover. Decoration only: nothing here throws,
 * and every failure is simply "no clip".
 *
 * - Sources are asked in order and the first answer that survives [CanvasMatcher] wins.
 * - A lookup runs in the repository's own scope, shared by everyone asking about the same track;
 *   when the last of them goes away (the track changed) the lookup, and its HTTP call, are cancelled.
 * - No global lock: only [maxConcurrentLookups] lookups touch the network at once.
 * - Hits, misses and "could not ask" are cached for different lengths of time.
 */
class CanvasRepository(
    private val providers: List<CanvasProvider>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = System::currentTimeMillis,
    private val providerTimeoutMs: Long = 6_000,
    private val totalTimeoutMs: Long = 14_000,
    maxConcurrentLookups: Int = 2,
    private val maxEntries: Int = 96,
    private val hitTtlMs: Long = 6L * 60 * 60 * 1000,
    private val missTtlMs: Long = 20L * 60 * 1000,
    private val inconclusiveTtlMs: Long = 2L * 60 * 1000,
) {
    private class Entry(val artwork: CanvasArtwork?, val storedAt: Long, val ttl: Long, val withAlbum: Boolean)

    private class Flight(val deferred: Deferred<CanvasArtwork?>) {
        var waiters = 0
    }

    private class Outcome(val artwork: CanvasArtwork?, val inconclusive: Boolean)

    private val lock = Any()
    private val limiter = Semaphore(maxConcurrentLookups)
    private val inflight = HashMap<String, Flight>()
    private val cache =
        object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = size > maxEntries
        }

    /** Whatever is already known about [query], without going near the network. */
    fun cached(query: CanvasQuery): CanvasCached? {
        if (!CanvasMatcher.isIdentifiable(query)) return CanvasCached(null)
        val key = CanvasMatcher.identity(query)
        return synchronized(lock) { usable(key, query)?.let { CanvasCached(it.artwork) } }
    }

    fun cached(metadata: MediaMetadata): CanvasCached? = metadata.toCanvasQuery()?.let(::cached)

    suspend fun canvasFor(metadata: MediaMetadata): CanvasArtwork? = metadata.toCanvasQuery()?.let { canvasFor(it) }

    /** The clip for [query], or null. Cancelling the caller cancels the lookup if nobody else needs it. */
    suspend fun canvasFor(query: CanvasQuery): CanvasArtwork? {
        if (providers.isEmpty() || !CanvasMatcher.isIdentifiable(query)) return null
        val key = CanvasMatcher.identity(query)
        val flight =
            synchronized(lock) {
                usable(key, query)?.let { return it.artwork }
                val running = inflight[key] ?: start(key, query).also { inflight[key] = it }
                running.also { it.waiters++ }
            }
        try {
            return flight.deferred.await()
        } finally {
            synchronized(lock) {
                flight.waiters--
                if (flight.waiters == 0 && !flight.deferred.isCompleted) {
                    flight.deferred.cancel()
                    if (inflight[key] === flight) inflight.remove(key)
                }
            }
        }
    }

    /** A cached entry that still answers [query], refreshed in LRU order. Call with [lock] held. */
    private fun usable(key: String, query: CanvasQuery): Entry? {
        val entry = cache[key] ?: return null
        if (clock() - entry.storedAt > entry.ttl) return null
        val ok =
            if (entry.artwork != null) {
                // A late album can only contradict a clip chosen without it.
                CanvasMatcher.albumMatches(query.album, entry.artwork.album)
            } else {
                // A miss reached blind is provisional once the album is known.
                entry.withAlbum || query.album.isNullOrBlank()
            }
        return entry.takeIf { ok }
    }

    private fun start(key: String, query: CanvasQuery): Flight {
        val deferred =
            scope.async {
                val self = coroutineContext[Job]
                try {
                    val outcome = limiter.withPermit { resolve(query) }
                    synchronized(lock) {
                        val ttl =
                            when {
                                outcome.artwork != null -> hitTtlMs
                                outcome.inconclusive -> inconclusiveTtlMs
                                else -> missTtlMs
                            }
                        cache[key] = Entry(outcome.artwork, clock(), ttl, !query.album.isNullOrBlank())
                    }
                    outcome.artwork
                } finally {
                    synchronized(lock) { if (inflight[key]?.deferred === self) inflight.remove(key) }
                }
            }
        return Flight(deferred)
    }

    private suspend fun resolve(query: CanvasQuery): Outcome {
        val deadline = System.nanoTime() + totalTimeoutMs * 1_000_000
        var inconclusive = false
        for (provider in providers) {
            val remainingMs = (deadline - System.nanoTime()) / 1_000_000
            if (remainingMs <= 0) {
                inconclusive = true
                break
            }
            var candidate: CanvasCandidate? = null
            try {
                candidate = withTimeout(minOf(providerTimeoutMs, remainingMs)) { provider.find(query) }
            } catch (_: TimeoutCancellationException) {
                inconclusive = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                inconclusive = true
            }
            val artwork = candidate?.let { verified(provider.source, query, it) }
            if (artwork != null) return Outcome(artwork, false)
        }
        return Outcome(null, inconclusive)
    }

    private fun verified(source: CanvasSource, query: CanvasQuery, candidate: CanvasCandidate): CanvasArtwork? {
        if (!CanvasUrls.isAcceptable(candidate.url) || !CanvasMatcher.accepts(query, candidate)) return null
        return CanvasArtwork(
            mediaId = query.mediaId,
            url = candidate.url,
            fallbackUrl = candidate.fallbackUrl?.takeIf { it != candidate.url && CanvasUrls.isAcceptable(it) },
            source = source,
            title = candidate.title,
            artist = candidate.artist,
            album = candidate.album,
            portraitUrl = candidate.portraitUrl?.takeIf { it != candidate.url && CanvasUrls.isAcceptable(it) },
        )
    }

    companion object {
        /** The app's repository, built on first use. */
        val shared: CanvasRepository by lazy { CanvasRepository(CanvasConfig.providers()) }
    }
}

/**
 * Null for anything a catalogue could not identify: podcasts, local files, no credits. A music
 * video is looked up as its song (title without "Official Video" and the like, channel names
 * without "VEVO" / "- Topic"), so it can get the Canvas that comes before its video.
 */
fun MediaMetadata.toCanvasQuery(): CanvasQuery? {
    if (isPodcast || id.isLocalMediaId()) return null
    val names = artists.map { if (isMusicVideo) app.vibra.player.cleanChannelName(it.name) else it.name }.filter { it.isNotBlank() }
    val songTitle = if (isMusicVideo) app.vibra.player.cleanVideoTitle(title, names) else title
    if (songTitle.isBlank() || names.isEmpty()) return null
    return CanvasQuery(id, songTitle, names, album?.title)
}
