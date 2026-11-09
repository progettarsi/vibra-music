/*
 * Vibra (2026) - an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune (github.com/rukamori/ArchiveTune).
 */

package app.vibra.ui.lyrics

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R

private const val ExportCache = "lyrics-export"
private const val PngMimeType = "image/png"
private const val CacheLifetimeMs = 24 * 60 * 60 * 1_000L

internal suspend fun writeLyricsCardToCache(context: Context, bitmap: Bitmap): File = withContext(Dispatchers.IO) {
    val directory = File(context.cacheDir, ExportCache).apply { mkdirs() }
    val expiresBefore = System.currentTimeMillis() - CacheLifetimeMs
    directory.listFiles()?.forEach { file -> if (file.lastModified() < expiresBefore) file.delete() }
    val file = File.createTempFile("VIBRA-lyrics-", ".png", directory)
    var complete = false
    try {
        file.outputStream().use { bitmap.writePng(it) }
        complete = true
        file
    } finally {
        if (!complete) file.delete()
    }
}

internal fun shareLyricsCard(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = PngMimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_lyrics)))
}

@RequiresApi(Build.VERSION_CODES.Q)
internal suspend fun saveLyricsCardToGallery(context: Context, bitmap: Bitmap) = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, lyricsCardFileName())
        put(MediaStore.MediaColumns.MIME_TYPE, PngMimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/VIBRA")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
    var complete = false
    try {
        checkNotNull(resolver.openOutputStream(uri)).use { bitmap.writePng(it) }
        val published = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        check(resolver.update(uri, published, null, null) == 1)
        complete = true
    } finally {
        if (!complete) runCatching { resolver.delete(uri, null, null) }
    }
}

internal suspend fun copyLyricsCardToDocument(context: Context, fileName: String, destination: Uri?) {
    withContext(Dispatchers.IO) {
        val file = File(File(context.cacheDir, ExportCache), fileName)
        var complete = false
        try {
            if (destination != null) {
                val resolver = context.contentResolver
                checkNotNull(resolver.openOutputStream(destination, "w")).use { output ->
                    file.inputStream().use { it.copyTo(output) }
                }
            }
            complete = true
        } finally {
            file.delete()
            if (!complete && destination != null) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            }
        }
    }
}

private fun Bitmap.writePng(output: OutputStream) {
    check(compress(Bitmap.CompressFormat.PNG, 100, output))
}

internal fun lyricsCardFileName(): String = "VIBRA-lyrics-${System.currentTimeMillis()}.png"
