/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 *
 * The reason for a dedicated clip cache is documented in BitChord's CanvasCache.kt
 * (github.com/kushagrasinghx/BitChord, GPL-3.0): a few seconds of video looping under a
 * four-minute track would otherwise be downloaded again at every loop.
 */

package app.vibra.canvas

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Disk cache for Canvas clips, separate from the audio caches. ExoPlayer frees samples as soon as
 * they are rendered, so without it every pass of the loop would ask the network for the same few
 * seconds again; with it only the first pass does, and a replay of the track is served from disk.
 *
 * One [SimpleCache] per process and directory (a second one on the same folder throws), opened on
 * first use and never released: it lives as long as the app.
 */
@UnstableApi
object CanvasClipCache {
    private const val LIMIT_BYTES = 128L * 1024 * 1024
    private val lock = Any()
    private var cache: SimpleCache? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followSslRedirects(false)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private fun cache(context: Context): SimpleCache =
        synchronized(lock) {
            cache ?: SimpleCache(
                File(context.applicationContext.cacheDir, "canvas"),
                LeastRecentlyUsedCacheEvictor(LIMIT_BYTES),
                StandaloneDatabaseProvider(context.applicationContext),
            ).also { cache = it }
        }

    /** Network behind the cache; a cache that cannot be written falls back to plain streaming. */
    fun dataSourceFactory(context: Context): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(
                OkHttpDataSource.Factory(client).setDefaultRequestProperties(mapOf("User-Agent" to CanvasConfig.USER_AGENT)),
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
