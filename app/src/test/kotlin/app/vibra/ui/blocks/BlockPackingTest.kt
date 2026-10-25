/*
 * Vibra (2026) — tests for the block grid packer (VibraBlocks.kt). GPL-3.0.
 */

package app.vibra.ui.blocks

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockPackingTest {
    private fun block(
        key: String,
        relevance: Float,
        max: BlockSize = BlockSize.Large,
    ) = VibraBlock(key = key, relevance = relevance, maxSize = max) { _, _ -> }

    private fun List<BlockRow>.keys() = map { row -> row.cells.map { it.block.key to it.span } }

    @Test
    fun relevance_picksSizeClass() {
        assertEquals(BlockSize.Large, block("a", 0.9f).size)
        assertEquals(BlockSize.Medium, block("b", 0.6f).size)
        assertEquals(BlockSize.Small, block("c", 0.2f).size)
        assertEquals(BlockSize.Small, block("d", 0.9f, max = BlockSize.Small).size)
    }

    @Test
    fun smallBlocks_pairOnPhones() {
        val rows = packBlocks(listOf(block("a", 0.3f), block("b", 0.3f)), columns = 2)
        assertEquals(listOf(listOf("a" to 1, "b" to 1)), rows.keys())
    }

    @Test
    fun laterSmallBlock_isPulledUpToFillTheRow() {
        val rows = packBlocks(listOf(block("s1", 0.3f), block("big", 0.9f), block("s2", 0.3f)), columns = 2)
        assertEquals(listOf(listOf("s1" to 1, "s2" to 1), listOf("big" to 2)), rows.keys())
    }

    @Test
    fun loneSmallBlock_isStretchedAndPromoted() {
        val rows = packBlocks(listOf(block("big", 0.9f), block("s", 0.3f)), columns = 2)
        assertEquals(listOf(listOf("big" to 2), listOf("s" to 2)), rows.keys())
        assertEquals(BlockSize.Medium, rows[1].cells.single().size)
    }

    @Test
    fun smallOnlyBlock_staysSmallWhenStretched() {
        val rows = packBlocks(listOf(block("s", 0.3f, max = BlockSize.Small)), columns = 2)
        assertEquals(BlockSize.Small, rows.single().cells.single().size)
        assertEquals(2, rows.single().cells.single().span)
    }

    @Test
    fun tablets_putTwoMediumsSideBySide() {
        val rows = packBlocks(listOf(block("m1", 0.6f), block("m2", 0.6f)), columns = 4)
        assertEquals(listOf(listOf("m1" to 2, "m2" to 2)), rows.keys())
    }
}
