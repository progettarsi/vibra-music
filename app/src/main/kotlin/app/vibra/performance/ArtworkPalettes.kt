/* Vibra (2026) — GPL-3.0. Derived from ArchiveTune © Rukamori. */
package app.vibra.performance

import android.content.Context
import android.net.Uri
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import java.io.File
import kotlinx.coroutines.*
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor

/** Stores swatches, never bitmaps, fallbacks, theme colours, or Activity contexts. */
object ArtworkPalettes {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cache = SharedResultCache<String, Palette>(64, 2, scope)

    suspend fun load(context: Context, url: String): Palette? {
        val application = context.applicationContext
        val key =
            withContext(Dispatchers.IO) {
                val uri = Uri.parse(url)
                val revision =
                    when (uri.scheme) {
                        "file" ->
                            File(uri.path.orEmpty()).let { "${it.lastModified()}:${it.length()}" }
                        "content" ->
                            runCatching {
                                    application.contentResolver
                                        .openAssetFileDescriptor(uri, "r")
                                        ?.use { descriptor ->
                                            val stat =
                                                android.system.Os.fstat(descriptor.fileDescriptor)
                                            "${stat.st_mtime}:${stat.st_size}:${descriptor.length}"
                                        }
                                        .orEmpty()
                                }
                                .getOrDefault("")
                        else -> ""
                    }
                "$url|$revision|${PlayerColorExtractor.Config.IMAGE_SIZE}|${PlayerColorExtractor.Config.MAX_COLOR_COUNT}|${PlayerColorExtractor.Config.BITMAP_AREA}"
            }
        return cache.get(key) {
            try {
                val request =
                    ImageRequest.Builder(application)
                        .data(url)
                        .size(
                            PlayerColorExtractor.Config.IMAGE_SIZE,
                            PlayerColorExtractor.Config.IMAGE_SIZE,
                        )
                        .memoryCacheKey(key)
                        .diskCacheKey(key)
                        .allowHardware(false)
                        .build()
                val bitmap =
                    application.imageLoader.execute(request).image?.toBitmap() ?: return@get null
                currentCoroutineContext().ensureActive()
                Palette.from(bitmap)
                    .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                    .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                    .generate()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }

    fun trim() {
        cache.clear()
    }
}
