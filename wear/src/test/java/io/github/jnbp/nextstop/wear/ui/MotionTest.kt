package io.github.jnbp.nextstop.wear.ui

import io.github.jnbp.nextstop.wear.data.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The arrival ring of the watch and the system switch for animations. */
class MotionTest {
    @Test
    fun animationsFollowTheSystemScale() {
        assertTrue(watchAnimationsAllowed(1f))
        assertFalse(watchAnimationsAllowed(0f))
        // The app's own setting "Animations" switches them off as well, and cannot switch on what the system switched off.
        assertTrue(watchAnimationsAllowed(1f, appSetting = true))
        assertFalse(watchAnimationsAllowed(1f, appSetting = false))
        assertFalse(watchAnimationsAllowed(0f, appSetting = true))
    }

    @Test
    fun rowsEnterOneAfterAnotherUpToALimit() {
        assertEquals(0L, enterDelayMillis(0))
        assertEquals(2 * ENTER_STEP_MILLIS, enterDelayMillis(2))
        assertEquals(ENTER_MAX_STEPS * ENTER_STEP_MILLIS, enterDelayMillis(40))
        assertEquals(0L, enterDelayMillis(-1))
    }

    @Test
    fun theRingGoesAllAroundTheEdge() {
        val pieces = edgeConfetti(EDGE_CONFETTI_COUNT, 3, seed = 3)
        assertEquals(EDGE_CONFETTI_COUNT, pieces.size)
        // Spread over the whole circle, within a few degrees of their even places.
        pieces.forEachIndexed { index, piece ->
            assertTrue(kotlin.math.abs(piece.angle - index * 360f / EDGE_CONFETTI_COUNT) <= 4f)
            assertTrue(piece.colorIndex in 0 until 3)
        }
    }

    @Test
    fun piecesFlyOutToTheEdgeAndFade() {
        val piece = edgeConfetti(EDGE_CONFETTI_COUNT, 2, seed = 1)[4]
        val start = edgePieceAt(piece, 0f)
        val out = edgePieceAt(piece, 0.45f)
        val end = edgePieceAt(piece, WATCH_ARRIVAL_SECONDS)
        assertEquals(0.55f, start[1], 1e-4f)
        assertEquals(0.92f, out[1], 1e-4f)
        assertTrue("never beyond the edge", out[1] < 1f)
        assertEquals(1f, start[2], 0f)
        assertEquals(0f, end[2], 0f)
        assertEquals(piece.angle, start[0], 0f)
    }

    @Test
    fun aTripBuzzesAtItsStartAtEachChangeMadeAndAtItsArrival() {
        val moments = TripMoments()
        val start = 1_000_000L
        assertEquals(listOf(WatchMoment.Start), moments.update("t1", true, StepKind.Board, start + 3_600_000, start, start + 5_000))
        assertEquals(emptyList<WatchMoment>(), moments.update("t1", true, StepKind.GetOff, start + 3_600_000, start, start + 60_000))
        assertEquals(emptyList<WatchMoment>(), moments.update("t1", true, StepKind.Board, start + 3_600_000, start, start + 900_000))
        // Riding again after a change: made.
        assertEquals(listOf(WatchMoment.ChangeMade), moments.update("t1", true, StepKind.GetOff, start + 3_600_000, start, start + 1_000_000))
        assertEquals(emptyList<WatchMoment>(), moments.update("t1", true, StepKind.GetOff, start + 3_600_000, start, start + 1_100_000))
        assertEquals(listOf(WatchMoment.Arrived), moments.update("t1", true, StepKind.Arrived, start + 3_600_000, start, start + 3_600_000))
        // Told once.
        assertEquals(emptyList<WatchMoment>(), moments.update("t1", true, StepKind.Arrived, start + 3_600_000, start, start + 3_610_000))
        assertEquals(emptyList<WatchMoment>(), moments.update(null, false, StepKind.Arrived, 0, 0, start + 3_700_000))
    }

    @Test
    fun whatWasSoWhenTheScreenOpenedIsNotToldAgain() {
        val start = 1_000_000L
        val late = TripMoments()
        // Opened long after the start, riding: no start buzz, and the ride counts as the first.
        assertEquals(emptyList<WatchMoment>(), late.update("t2", true, StepKind.GetOff, start + 3_600_000, start, start + 600_000))
        val ended = TripMoments()
        assertEquals(emptyList<WatchMoment>(), ended.update(null, false, StepKind.Arrived, start, start, start + 10_000))
    }

    @Test
    fun aTripEndedEarlyIsNotAnArrivalButOneEndedAtItsTimeIs() {
        val start = 1_000_000L
        val early = TripMoments()
        early.update("t3", true, StepKind.GetOff, start + 3_600_000, start, start + 5_000)
        assertEquals(emptyList<WatchMoment>(), early.update("t3", false, StepKind.Arrived, start + 3_600_000, start, start + 600_000))
        val onTime = TripMoments()
        onTime.update("t4", true, StepKind.GetOff, start + 3_600_000, start, start + 5_000)
        assertEquals(listOf(WatchMoment.Arrived), onTime.update("t4", false, StepKind.Arrived, start + 3_600_000, start, start + 3_570_000))
    }
}
