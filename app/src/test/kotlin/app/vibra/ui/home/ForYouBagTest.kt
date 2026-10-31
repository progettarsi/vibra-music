/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.home
import org.junit.Assert.*
import org.junit.Test

class ForYouBagTest {
    @Test fun emptyAndSingle() {
        val bag = ForYouBag()
        assertNull(bag.choose(emptyList(), null, null))
        repeat(3) { assertEquals("one", bag.choose(listOf("one"), "one", "one")) }
    }
    @Test fun noRepeatsBeforeExhaustion() {
        repeat(50) {
            val bag = ForYouBag()
            val picks = List(6) { bag.choose(listOf("a", "b", "c", "d", "e", "f"), null, null) }
            assertEquals(6, picks.toSet().size)
        }
    }
    @Test fun excludesCurrentAndFirstWhenAlternativesExist() {
        val bag = ForYouBag()
        repeat(30) {
            val picked = bag.choose(listOf("first", "current", "a", "b"), "current", "first")
            assertTrue(picked in listOf("a", "b"))
        }
    }
    @Test fun poolChangesDropUnavailableEntries() {
        val bag = ForYouBag()
        bag.choose(listOf("a", "b"), null, null)
        assertEquals("c", bag.choose(listOf("c", "c"), null, null))
    }
}
