/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package moe.rukamori.archivetune.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A new credential preference must be added to the backup exclusions, or this fails. */
class BackupCredentialKeysTest {
    private val credentialName = Regex("token|cookie|secret|password|session|api_?key|sp_dc|sp_key|savedaccounts", RegexOption.IGNORE_CASE)
    private val declaredKey = Regex("""PreferencesKey\("([^"]+)"\)""")

    @Test fun perAccountKeysFollowTheAccountCategory() {
        val withoutAccount = BackupArchiveRepository.ACCOUNT_PREFERENCE_KEYS
        assertTrue(BackupArchiveRepository.isExcludedPreference("spotify_sync_completed_user1", withoutAccount))
        assertTrue(BackupArchiveRepository.isExcludedPreference("innerTubeCookie", withoutAccount))
        assertFalse(BackupArchiveRepository.isExcludedPreference("customThemeColor", withoutAccount))
        assertFalse(BackupArchiveRepository.isExcludedPreference("spotify_sync_completed_user1", emptySet()))
    }

    @Test fun everyCredentialPreferenceIsExcludedWithoutTheAccountCategory() {
        val source = File("src/main/kotlin/moe/rukamori/archivetune/constants/PreferenceKeys.kt").readText()
        val declared = declaredKey.findAll(source).map { it.groupValues[1] }.toList()
        assertTrue("PreferenceKeys.kt was not read", declared.size > 100)

        val credentials = declared.filter(credentialName::containsMatchIn)
        assertTrue("the search must see known credentials", "innerTubeCookie" in credentials)
        assertEquals(
            emptyList<String>(),
            credentials.filterNot { it in BackupArchiveRepository.ACCOUNT_PREFERENCE_KEYS },
        )
    }
}
