/*
 * Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */
package app.vibra.ui.player

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PlayerArtworkFlightTest {
    private val viewport = Rect(0f,0f,1080f,2400f)
    private val pill = Rect(224f,2137f,856f,2284f)
    private val slot = Rect(0f,269f,1080f,1349f)
    private val cookie = Rect(239.75f,2152.75f,355.25f,2268.25f)
    private fun pose(p: Float) = artworkFlightPose(slot,cookie,viewport,playerContainerRect(pill,viewport,p),p)
    private fun contains(outer: Rect, inner: Rect) = outer.left <= inner.left+.002f && outer.top <= inner.top+.002f && outer.right+.002f >= inner.right && outer.bottom+.002f >= inner.bottom

    @Test fun cropStartsAtHalfAndVideoUsesTheReverseCurve() {
        for(c in listOf(0f,.25f,.5f)) assertEquals(0f,closingCrop(c),0f)
        assertTrue(closingCrop(.6f)>0f)
        assertEquals(.5f,closingCrop(.75f),.00001f)
        assertEquals(1f,closingCrop(1f),.00001f)
        for(p in listOf(0f,.25f,.5f)) assertEquals(0f,artworkVideoOpacity(p),0f)
        assertEquals(.5f,artworkVideoOpacity(.75f),.00001f)
        assertEquals(1f,artworkVideoOpacity(1f),0f)
    }
    @Test fun geometryIsTheSameInBothDirectionsAtEveryPercent() {
        val opening=PlayerArtworkFlight();val closing=PlayerArtworkFlight()
        val up=(0..100).map{opening.sample(it/100f,14f)}
        val down=(100 downTo 0).map{closing.sample(it/100f,14f)}.reversed()
        up.zip(down).forEach{(a,b)->assertEquals(a,b)}
    }
    @Test fun interruptionNeverSwitchesGeometryOrOpacity() {
        val state=PlayerArtworkFlight()
        for(p in listOf(0f,.25f,.4f,.1f,.6f,.3f,1f,.8f,.2f,.7f,0f)) {
            val f=state.sample(p,14f)
            assertEquals(closingCrop(1-p),f.tail,0f)
            assertEquals(artworkVideoOpacity(p),f.videoOpacity,0f)
            assertEquals(14f*f.tail,f.angle,.00001f)
        }
    }
    @Test fun fieldCoversMaskAndMaskStaysInsidePlayerAtEveryStep() {
        for(i in 0..1000) {
            val p=i/1000f;val a=pose(p);val c=playerContainerRect(pill,viewport,p)
            assertTrue("field $p",contains(a.field,a.crop))
            assertTrue("container $p",contains(c,a.crop))
            assertTrue("source $p",contains(viewport,a.cropInSource))
            assertTrue(a.scale>0f)
        }
    }
    @Test fun noExposedSideBandsDuringFirstHalf() {
        for(i in 50..100) {
            val p=i/100f;val a=pose(p);val c=playerContainerRect(pill,viewport,p)
            assertEquals(c,a.crop)
            assertTrue(contains(a.field,c))
        }
    }
    @Test fun endpointsMatchOpenFieldAndOriginalCookie() {
        val open=pose(1f);assertEquals(1f,open.scale,0f)
        assertEquals(0f,open.translation.x,0f);assertEquals(0f,open.translation.y,0f)
        assertEquals(viewport,open.crop)
        val shut=pose(0f);assertEquals(cookie,shut.crop)
        assertEquals(slot.left,shut.cropInSource.left,.002f)
        assertEquals(slot.bottom,shut.cropInSource.bottom,.002f)
        assertEquals(slot.width*shut.scale,cookie.width,.002f)
    }
    @Test fun maskAndTransformHaveNoJumpAtHalf() {
        val a=pose(.50001f);val b=pose(.49999f)
        assertTrue(abs(a.scale-b.scale)<.001f)
        assertTrue(abs(a.translation.x-b.translation.x)<.1f)
        assertTrue(abs(a.crop.top-b.crop.top)<.1f)
    }
    @Test fun angleCrossesFifteenThirtyAndWrapWithoutSnaps() {
        for(start in listOf(14.9f,29.9f,359.9f)) {
            val f=PlayerArtworkFlight();f.sample(1f,start);f.sample(.7f,start)
            val a=f.sample(.02f,start);val b=f.sample(.02f,(start+.2f)%360f)
            assertTrue(abs(a.angle-b.angle)<.21f)
        }
    }
    @Test fun finalAngleFollowsSharedPhaseAndVelocity() {
        val f=PlayerArtworkFlight();f.sample(1f,359f);f.sample(.7f,359f)
        val a=f.sample(.0011f,359.9f);val b=f.sample(.0011f,.1f)
        assertEquals(.2f,b.angle-a.angle,.0001f);assertEquals(.1f,b.angle,.001f)
    }
    @Test fun reversalKeepsTheSameAngularAnchor() {
        val f=PlayerArtworkFlight();f.sample(0f,14f)
        val a=f.sample(.1f,14f);f.sample(.8f,14f);val b=f.sample(.1f,14f)
        assertEquals(a,b)
    }
    @Test fun handoffHasExactlyOneOwner() {
        assertFalse(artworkInMiniPlayer(.002f));assertTrue(artworkInMiniPlayer(HandoffProgress))
        assertTrue(artworkInMiniPlayer(0f))
    }
    @Test fun curvesClampAndStartAndEndSmoothly() {
        assertEquals(0f,closingCrop(-1f),0f);assertEquals(1f,closingCrop(2f),0f)
        assertEquals(0f,artworkVideoOpacity(-1f),0f);assertEquals(1f,artworkVideoOpacity(2f),0f)
        assertTrue(closingCrop(.5001f)<.00001f)
        assertTrue(1f-closingCrop(.9999f)<.00001f)
    }
}
