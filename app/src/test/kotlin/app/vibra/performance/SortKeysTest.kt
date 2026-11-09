/* Vibra (2026) — GPL-3.0. */
package app.vibra.performance

import java.text.Collator
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class SortKeysTest {
    @Test
    fun keysAreComputedOnceAndLocaleOrderIsPreserved() {
        val names = listOf("Zucchero", "Èlia", "Elia", "Álvaro", "Alvaro", "Björk")
        val collator = Collator.getInstance(Locale.ITALIAN).apply { strength = Collator.PRIMARY }
        var evaluations = 0
        val actual =
            names.sortedByArtistKey(collator) {
                evaluations++
                it
            }
        assertEquals(names.size, evaluations)
        assertEquals(names.sortedWith(collator), actual)
    }
}
