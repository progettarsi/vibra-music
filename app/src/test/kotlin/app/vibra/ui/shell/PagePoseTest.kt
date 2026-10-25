/*
 * Vibra (2026) — GPL-3.0.
 */

package app.vibra.ui.shell

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PagePoseTest {
    private val page = Size(1080f, 2400f)
    private val button = Rect(left = 40f, top = 2200f, right = 200f, bottom = 2360f)

    @Test
    fun originRect_runsFromTheElementToThePage() {
        assertEquals(button, PagePose.originRect(button, page, 0f))
        assertEquals(Rect(0f, 0f, page.width, page.height), PagePose.originRect(button, page, 1f))
        val half = PagePose.originRect(button, page, 0.5f)
        assertEquals(20f, half.left, 0.01f)
        assertEquals(1100f, half.top, 0.01f)
    }

    @Test
    fun coverScale_fillsTheContainerWithoutGaps() {
        val square = Size(160f, 160f)
        val scale = PagePose.coverScale(square, page)
        assertTrue(page.width * scale >= square.width - 0.01f)
        assertTrue(page.height * scale >= square.height - 0.01f)
        // A wide row is covered by the width, a tall strip by the height.
        assertEquals(1000f / 1080f, PagePose.coverScale(Size(1000f, 180f), page), 0.0001f)
        assertEquals(1200f / 2400f, PagePose.coverScale(Size(100f, 1200f), page), 0.0001f)
    }

    @Test
    fun originFades_containerFirstThenContent() {
        assertEquals(0f, PagePose.containerAlpha(0f))
        assertEquals(1f, PagePose.containerAlpha(PagePose.ContainerFadeSpan))
        assertEquals(0f, PagePose.contentAlpha(PagePose.ContainerFadeSpan))
        assertEquals(1f, PagePose.contentAlpha(1f))
    }

    @Test
    fun pushedPage_staysSolidThenDissolvesNearTheEnd() {
        assertEquals(1f, PagePose.pushAlpha(1f))
        assertEquals(1f, PagePose.pushAlpha(PagePose.PushFadeEnd))
        assertEquals(0f, PagePose.pushAlpha(PagePose.PushFadeStart))
        assertEquals(0f, PagePose.pushAlpha(0f))
    }

    @Test
    fun fadeThrough_neverShowsBothPages() {
        for (step in 0..100) {
            val p = step / 100f
            val beneath = PagePose.fadeOutAlpha(covered = p)
            val results = PagePose.fadeInAlpha(p)
            assertTrue("both visible at $p", beneath == 0f || results == 0f)
        }
        assertEquals(1f, PagePose.fadeOutAlpha(0f))
        assertEquals(1f, PagePose.fadeInAlpha(1f))
    }

    @Test
    fun gestureProgress_keepsBothEnds() {
        assertEquals(1f, PagePose.progress(1f, predictive = true), 0.0001f)
        assertEquals(0f, PagePose.progress(0f, predictive = true), 0.0001f)
        assertEquals(0.3f, PagePose.progress(0.3f, predictive = false), 0.0001f)
    }

    @Test
    fun gestureProgress_answersTheFingerAtOnce() {
        // A quarter of the swipe moves the page more than a quarter of its way back.
        assertTrue(PagePose.progress(0.75f, predictive = true) < 0.75f)
        // …and never moves backwards while the finger goes on.
        var last = 1f
        for (step in 1..100) {
            val pose = PagePose.progress(1f - step / 100f, predictive = true)
            assertTrue(pose <= last + 1e-4f)
            last = pose
        }
    }

    @Test
    fun scrim_followsHowMuchOfThePageIsCovered() {
        assertEquals(0f, PagePose.scrim(0f))
        assertEquals(PagePose.MaxScrim, PagePose.scrim(1f))
        assertEquals(PagePose.MaxScrim, PagePose.scrim(2f))
    }
}
