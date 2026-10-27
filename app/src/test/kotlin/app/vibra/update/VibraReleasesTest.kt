/*
 * Vibra (2026) — tests for reading releases and comparing versions (VibraRelease.kt). GPL-3.0.
 */

package app.vibra.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VibraReleasesTest {
    private fun release(
        tag: String = "v1.2",
        assets: String = apk("VIBRA-1.2-release.apk"),
        extra: String = "",
    ) = """{"tag_name":"$tag","name":"VIBRA 1.2","draft":false,"prerelease":false,$extra"assets":[$assets]}"""

    private fun apk(
        name: String,
        digest: String? = "sha256:ABCDEF",
    ) = """{"name":"$name","size":34284518,""" +
        (digest?.let { "\"digest\":\"$it\"," } ?: "") +
        """"browser_download_url":"https://github.com/o/r/releases/download/v1.2/$name"}"""

    @Test
    fun parsesVersionApkSizeAndDigest() {
        val parsed = VibraReleases.parseLatest(release())!!
        assertEquals("1.2", parsed.versionName)
        assertEquals("https://github.com/o/r/releases/download/v1.2/VIBRA-1.2-release.apk", parsed.apkUrl)
        assertEquals(34284518L, parsed.apkSize)
        assertEquals("abcdef", parsed.sha256)
    }

    @Test
    fun prefersTheReleaseApk() {
        val parsed = VibraReleases.parseLatest(release(assets = apk("VIBRA-1.2-debug.apk") + "," + apk("VIBRA-1.2-release.apk")))!!
        assertTrue(parsed.apkUrl.endsWith("VIBRA-1.2-release.apk"))
    }

    @Test
    fun noDigestMeansNoChecksum() {
        assertNull(VibraReleases.parseLatest(release(assets = apk("VIBRA-1.2-release.apk", digest = null)))!!.sha256)
    }

    @Test
    fun skipsDraftsPreReleasesAndReleasesWithoutApk() {
        assertNull(VibraReleases.parseLatest(release(assets = apk("notes.txt"))))
        assertNull(VibraReleases.parseLatest(release(tag = "latest")))
        assertNull(VibraReleases.parseLatest(release().replace("\"prerelease\":false", "\"prerelease\":true")))
        assertNull(VibraReleases.parseLatest(release().replace("\"draft\":false", "\"draft\":true")))
        assertNull(VibraReleases.parseLatest("""{"message":"Not Found"}"""))
        assertNull(VibraReleases.parseLatest("not json"))
    }

    @Test
    fun comparesVersionsNumerically() {
        assertTrue(VibraReleases.isNewer("1.1", "1.0.8"))
        assertTrue(VibraReleases.isNewer("v1.10", "1.9"))
        assertTrue(VibraReleases.isNewer("2.0", "1.9.9"))
        assertFalse(VibraReleases.isNewer("1.1", "1.1"))
        assertFalse(VibraReleases.isNewer("1.1.0", "1.1"))
        assertFalse(VibraReleases.isNewer("1.0.8", "1.1"))
        assertFalse(VibraReleases.isNewer("nightly", "1.1"))
    }
}
