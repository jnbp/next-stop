package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A course opened from a vehicle looks at the vehicle and the road ahead, its end names stay readable. */
class CourseFrameTest {
    private val stops: List<Pair<Double, Double>?> = (0 until 20).map { 52.50 + it * 0.005 to 13.20 }
    private val vehicle = 52.5125 to 13.20

    @Test
    fun vehicleAndItsNextStops() {
        // Halfway between the third stop (index 2) and the fourth.
        val frame = aheadFrame(stops, 2.5f, vehicle)
        assertEquals(listOf(vehicle) + stops.subList(3, 8), frame)
    }

    @Test
    fun stopsWithoutAPositionAreSkipped() {
        val gaps = stops.mapIndexed { index, stop -> if (index == 4) null else stop }
        assertEquals(listOf(vehicle) + listOf(stops[3], stops[5], stops[6], stops[7], stops[8]), aheadFrame(gaps, 2.5f, vehicle))
    }

    @Test
    fun nearTheEndOnlyTheStopsAheadFrame() {
        // One stop before the end: the vehicle and that end, never the stops it has passed.
        assertEquals(listOf(vehicle, stops[19]), aheadFrame(stops, 18.2f, vehicle))
        // At the end: the last stop.
        assertEquals(listOf(stops[19]), aheadFrame(stops, 19f, null))
    }

    @Test
    fun withoutProgressTheWholeLine() {
        assertEquals(stops.filterNotNull(), aheadFrame(stops, null, vehicle))
    }

    @Test
    fun endNameStaysInside() {
        // Screen 1000 wide, free from y 200 to 1400; a name 300 wide.
        fun spot(x: Float, y: Float) = mapLabelSpot(x, y, 300f, 40f, 0f, 200f, 1000f, 1400f, gap = 20f, margin = 16f)
        // Room on the right: right of its ring.
        assertEquals(120f to 580f, spot(100f, 600f))
        // At the right edge: flipped to the left.
        assertEquals(670f to 580f, spot(990f, 600f))
        // A ring off the right edge: the name pushed inside, whole.
        assertEquals(684f to 580f, spot(1200f, 600f))
        // Under the header: pushed down into the free part.
        assertEquals(120f to 216f, spot(100f, 150f))
        // No room on either side of the ring on a narrow screen: pushed inside, whole.
        assertEquals(84f to 580f, mapLabelSpot(200f, 600f, 300f, 40f, 0f, 200f, 400f, 1400f, 20f, 16f))
        // Wider than the screen: from the left margin.
        assertEquals(16f to 580f, mapLabelSpot(200f, 600f, 500f, 40f, 0f, 200f, 400f, 1400f, 20f, 16f))
    }

    @Test
    fun aStopInViewGetsNoPointer() {
        val out = FloatArray(3)
        assertFalse(edgePointer(500f, 800f, 0f, 200f, 1000f, 1400f, 20f, out))
        assertFalse(edgePointer(Float.NaN, 800f, 0f, 200f, 1000f, 1400f, 20f, out))
    }

    @Test
    fun aStopAboveIsPointedAtFromTheTopEdge() {
        // Free from y 200 to 1400, middle at (500, 800); the stop straight above, far away.
        val out = FloatArray(3)
        assertTrue(edgePointer(500f, -3000f, 0f, 200f, 1000f, 1400f, 20f, out))
        assertEquals(500f, out[0], 1e-3f)
        assertEquals(220f, out[1], 1e-3f)
        assertEquals(0f, out[2], 1e-3f)
    }

    @Test
    fun aStopUnderTheSheetIsPointedAtDownwardsInsideTheFreePart() {
        // Below the free part (under the sheet), to the right: the pointer stays on the free part's edge.
        val out = FloatArray(3)
        assertTrue(edgePointer(900f, 2000f, 0f, 200f, 1000f, 1400f, 20f, out))
        assertTrue(out[0] in 20f..980f)
        assertEquals(1380f, out[1], 1e-3f)
        assertTrue(out[2] > 90f && out[2] < 180f)
    }

    @Test
    fun aStopToTheLeftIsPointedAtFromTheLeftEdge() {
        val out = FloatArray(3)
        assertTrue(edgePointer(-500f, 800f, 0f, 200f, 1000f, 1400f, 20f, out))
        assertEquals(20f, out[0], 1e-3f)
        assertEquals(800f, out[1], 1e-3f)
        assertEquals(-90f, out[2], 1e-3f)
    }
}
