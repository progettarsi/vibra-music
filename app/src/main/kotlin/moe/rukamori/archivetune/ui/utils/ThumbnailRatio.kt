/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import moe.rukamori.archivetune.innertube.models.YTItem

private const val SQUARE_RATIO = 1f

val YTItem.thumbnailSourceRatio: Float?
    get() {
        val width = thumbnailWidth
        val height = thumbnailHeight
        return if (width != null && height != null && width > 0 && height > 0) {
            width.toFloat() / height.toFloat()
        } else {
            null
        }
    }

val YTItem.preferredThumbnailRatio: Float
    get() = SQUARE_RATIO
