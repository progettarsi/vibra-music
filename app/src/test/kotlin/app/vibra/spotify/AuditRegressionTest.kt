/* Vibra (2026) — audit regression tests, GPL-3.0. Derived from ArchiveTune © Rukamori. */
package app.vibra.spotify

import moe.rukamori.archivetune.backup.BackupArchiveRepository
import moe.rukamori.archivetune.betterlyrics.TTMLParser
import org.junit.Assert.*
import org.junit.Test

class AuditRegressionTest {
    @Test fun settingsOnlyBackupMustExcludeSavedSessionCookies() {
        assertTrue("savedAccounts contains innerTubeCookie and must be excluded",
            "savedAccounts" in BackupArchiveRepository.ACCOUNT_PREFERENCE_KEYS)
    }
    @Test fun settingsOnlyBackupMustExcludeAiApiKey() {
        assertTrue("ai_api_key must be excluded",
            "ai_api_key" in BackupArchiveRepository.ACCOUNT_PREFERENCE_KEYS)
    }
    @Test fun manuallyAddedTrackFromAnotherImportMustProtectPlaylist() {
        // A originally imported [a]. User adds b, already imported via another playlist.
        assertFalse(isUntouchedImport(null, listOf("a", "b"), playlistSnapshot(listOf("a"))))
    }
    @Test fun ttmlRejectsExternalEntityBeforeResolution() {
        val input = """<?xml version="1.0"?>
            <!DOCTYPE tt [<!ENTITY secret SYSTEM "file:///nonexistent-vibra-audit-fixture">]>
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="0s" end="1s">&secret;</p></div></body></tt>"""
        assertTrue(TTMLParser.parseDocument(input).isFailure)
    }
}
