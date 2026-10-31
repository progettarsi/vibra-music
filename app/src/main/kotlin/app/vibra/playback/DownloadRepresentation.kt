/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.playback

import moe.rukamori.archivetune.playback.stream.ResolvedAudioStream
import java.security.MessageDigest

/** Stable across renewed signed URLs, different across provider/format/account representations. */
internal fun downloadRepresentationSignature(stream: ResolvedAudioStream): String {
    // With incomplete format metadata, a renewed URL cannot prove byte compatibility.
    val unverifiedUrl = if (stream.formatId <= 0 || stream.contentLength <= 0) stream.url else ""
    val value = listOf(unverifiedUrl, stream.source.name, stream.authFingerprint, stream.formatId, stream.mimeType,
        stream.codecs, stream.bitrate, stream.sampleRate, stream.contentLength).joinToString("|")
    return MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
