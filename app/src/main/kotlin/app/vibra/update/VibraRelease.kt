/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A published VIBRA release: its version and the APK that installs it. */
data class VibraRelease(
    val versionName: String,
    val apkUrl: String,
    val apkSize: Long,
    /** Lower-case hex SHA-256 of the APK, when the source declares it. */
    val sha256: String?,
)

/** Pure rules for reading a GitHub "latest release" and comparing versions. */
internal object VibraReleases {
    private val VersionPattern = Regex("""\d+(?:\.\d+)*""")
    private const val Sha256Prefix = "sha256:"

    /**
     * Reads the JSON of `GET /repos/{owner}/{repo}/releases/latest`. Returns null for drafts,
     * pre-releases, tags without a version and releases without an APK. The release build is
     * preferred when a release carries more than one APK.
     */
    fun parseLatest(json: String): VibraRelease? {
        val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
        if (root.flag("draft") || root.flag("prerelease")) return null
        val version = root.text("tag_name")?.let(::versionOf) ?: return null
        val apks =
            root["assets"]
                ?.let { runCatching { it.jsonArray }.getOrNull() }
                .orEmpty()
                .mapNotNull { runCatching { it.jsonObject }.getOrNull() }
                .filter { it.text("name")?.endsWith(".apk", ignoreCase = true) == true }
        val apk = apks.firstOrNull { it.text("name")!!.contains("release", ignoreCase = true) } ?: apks.firstOrNull()
        val url = apk?.text("browser_download_url") ?: return null
        return VibraRelease(
            versionName = version,
            apkUrl = url,
            apkSize = apk["size"]?.jsonPrimitive?.longOrNull ?: -1L,
            sha256 =
                apk
                    .text("digest")
                    ?.takeIf { it.startsWith(Sha256Prefix, ignoreCase = true) }
                    ?.substring(Sha256Prefix.length)
                    ?.lowercase(),
        )
    }

    /** "v1.0.8" → "1.0.8"; null when the text holds no version. */
    fun versionOf(tag: String): String? = VersionPattern.find(tag)?.value

    /** True when [latest] is a higher version than [current] ("1.1" > "1.0.8", "1.1" == "1.1.0"). */
    fun isNewer(
        latest: String,
        current: String,
    ): Boolean {
        val a = parts(latest) ?: return false
        val b = parts(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (diff != 0) return diff > 0
        }
        return false
    }

    private fun parts(version: String): List<Int>? =
        versionOf(version)?.split('.')?.map { it.toIntOrNull() ?: return null }

    private fun JsonObject.text(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun JsonObject.flag(key: String): Boolean =
        runCatching { this[key]?.jsonPrimitive?.booleanOrNull }.getOrNull() == true
}
