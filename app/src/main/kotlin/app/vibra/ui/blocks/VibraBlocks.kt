/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vibra.ui.theme.VibraDimens

/*
 * ─────────────────────────────────────────────────────────────────────────────────────
 *  BLOCKS — Vibra's widget-like layout system (home, profile, library)
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 *  Instead of endless shelves, a screen is a list of *blocks* (the Figma cards: ForYou,
 *  songsCard, coverCard, playlistCard, playlistsCard, albumCollectionCard, favourites…).
 *  Every block declares how *relevant* it is right now (0‥1); relevance picks its size:
 *
 *        relevance ≥ 0.80   LARGE    full width, richest layout (e.g. 1 big + 4 covers)
 *        relevance ≥ 0.55   MEDIUM   full width on phones, half on tablets
 *        otherwise          SMALL    half width on phones, quarter on tablets
 *
 *  Blocks also clamp the sizes they support (minSize/maxSize), so content is never asked
 *  for a layout it cannot draw. The grid then packs blocks into rows:
 *    • order is preserved, except that a later block may be pulled up to fill a gap in
 *      the current row (looking at most [PackLookahead] blocks ahead);
 *    • a block left alone in a row with free space is stretched to fill it, so rows are
 *      always flush — an empty profile and a very full one both look composed;
 *    • rows of small blocks get a common height from the width, so neighbours align.
 *  Each row is one lazy item with `animateItem()`, so when data arrives and relevance
 *  changes, blocks resize and reflow with the list's spring animations.
 * ─────────────────────────────────────────────────────────────────────────────────────
 */

/** Size class a block is rendered at. */
enum class BlockSize { Small, Medium, Large }

/**
 * One block of a block screen.
 *
 * @param relevance 0‥1, how important the block is for this user right now.
 * @param content draws the block at the size class the grid assigned.
 */
@Immutable
class VibraBlock(
    val key: String,
    val relevance: Float,
    val minSize: BlockSize = BlockSize.Small,
    val maxSize: BlockSize = BlockSize.Large,
    val content: @Composable (size: BlockSize, modifier: Modifier) -> Unit,
) {
    /** Size class derived from relevance and clamped to what the block supports. */
    val size: BlockSize =
        when {
            relevance >= LargeThreshold -> BlockSize.Large
            relevance >= MediumThreshold -> BlockSize.Medium
            else -> BlockSize.Small
        }.let { if (it < minSize) minSize else if (it > maxSize) maxSize else it }
}

/** A block placed in a row: the units it spans and the size it is drawn at. */
@Immutable
internal data class BlockCell(
    val block: VibraBlock,
    val span: Int,
    val size: BlockSize,
)

/** A packed row of blocks. */
@Immutable
internal data class BlockRow(
    val cells: List<BlockCell>,
) {
    val key: String = cells.joinToString("|") { it.block.key }

    /** Rows with small blocks share a width-derived height so neighbours line up. */
    val hasSmall: Boolean = cells.any { it.size == BlockSize.Small }
}

/** Grid units a size class spans with [columns] units per row (2 on phones, 4 on tablets). */
internal fun BlockSize.span(columns: Int): Int =
    when (this) {
        BlockSize.Large -> columns
        BlockSize.Medium -> if (columns >= 4) columns / 2 else columns
        BlockSize.Small -> 1
    }

/** Packs [blocks] into flush rows (see the file header for the rules). */
internal fun packBlocks(
    blocks: List<VibraBlock>,
    columns: Int,
): List<BlockRow> {
    val pending = blocks.toMutableList()
    val rows = mutableListOf<BlockRow>()
    while (pending.isNotEmpty()) {
        val cells = mutableListOf<BlockCell>()
        var free = columns
        val first = pending.removeAt(0)
        val firstSpan = first.size.span(columns).coerceAtMost(columns)
        cells += BlockCell(first, firstSpan, first.size)
        free -= firstSpan
        while (free > 0) {
            val candidate =
                pending
                    .take(PackLookahead)
                    .firstOrNull { it.size.span(columns) <= free }
                    ?: break
            pending.remove(candidate)
            val span = candidate.size.span(columns)
            cells += BlockCell(candidate, span, candidate.size)
            free -= span
        }
        if (free > 0) {
            // Nothing fits the gap: stretch the last block so the row stays flush. A small
            // block alone in a row is promoted to the medium layout when it supports one.
            val last = cells.removeAt(cells.lastIndex)
            val promoted =
                if (cells.isEmpty() && last.size == BlockSize.Small && last.block.maxSize > BlockSize.Small) {
                    BlockSize.Medium
                } else {
                    last.size
                }
            cells += last.copy(span = last.span + free, size = promoted)
        }
        rows += BlockRow(cells)
    }
    return rows
}

/**
 * Emits [blocks] as packed rows into a LazyColumn.
 *
 * @param columns grid units per row; use [rememberBlockColumns] from the screen.
 * @param onReachEnd called when the last row is composed (feed pagination).
 */
fun LazyListScope.blockGrid(
    blocks: List<VibraBlock>,
    columns: Int,
    gap: Dp = VibraDimens.CardGap,
    onReachEnd: (() -> Unit)? = null,
    edgePadding: Dp = 0.dp,
    rowGap: Dp = 0.dp,
) {
    val rows = packBlocks(blocks, columns)
    rows.forEachIndexed { index, row ->
        item(key = "blocks_${row.key}", contentType = "block_row_${row.cells.size}") {
            Row(
                modifier =
                    Modifier
                        .animateItem()
                        // For lists without their own content padding / item spacing.
                        .padding(start = edgePadding, end = edgePadding, bottom = rowGap)
                        .fillMaxWidth()
                        .then(
                            // Rows holding small blocks share a height derived from the width,
                            // so side-by-side blocks always line up.
                            if (row.hasSmall) Modifier.aspectRatio(columns / SmallBlockHeightUnits) else Modifier,
                        ),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                row.cells.forEach { cell ->
                    Box(modifier = Modifier.weight(cell.span.toFloat()).then(if (row.hasSmall) Modifier.fillMaxHeight() else Modifier)) {
                        cell.block.content(
                            cell.size,
                            if (row.hasSmall) Modifier.fillMaxWidth().fillMaxHeight() else Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (onReachEnd != null && index == rows.lastIndex) {
                LaunchedEffect(row.key) { onReachEnd() }
            }
        }
    }
}

/** Grid units for a given content width: 2 on phones, 4 from 600 dp (tablets, landscape). */
fun blockColumnsFor(width: Dp): Int = if (width >= 600.dp) 4 else 2

private const val LargeThreshold = 0.8f
private const val MediumThreshold = 0.55f

/** How many blocks ahead the packer may pull one up to fill a row. */
private const val PackLookahead = 3

/** Height of a small-block row, in grid units of width (≈ square small blocks). */
private const val SmallBlockHeightUnits = 1.08f
