package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Tightness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure parts of the app's small animations: the stagger, the roll, the train, the grow and the confetti. */
class DelightTest {
    @Test
    fun animationsFollowTheSystemScale() {
        assertTrue(animationsAllowed(1f))
        assertTrue(animationsAllowed(0.5f))
        assertFalse(animationsAllowed(0f))
    }

    @Test
    fun listItemsComeInOneAfterTheOtherUpToACap() {
        assertEquals(0L, staggerDelay(0))
        assertEquals(STAGGER_ITEM_MILLIS, staggerDelay(1))
        assertEquals(3 * STAGGER_ITEM_MILLIS, staggerDelay(3))
        assertEquals(staggerDelay(8), staggerDelay(30))
        assertEquals(0L, staggerDelay(-2))
    }

    @Test
    fun countdownsRollUpAndGrowingNumbersRollDown() {
        assertTrue(rollsUp(5, 4))
        assertFalse(rollsUp(4, 6))
        assertTrue(rollsUp(null, 3))
        assertTrue(rollsUp(3, 3))
    }

    @Test
    fun theTrainEntersOnTheLeftAndLeavesOnTheRight() {
        assertEquals(-30f, trainOffset(0f, 100f, 30f), 1e-4f)
        assertEquals(100f, trainOffset(1f, 100f, 30f), 1e-4f)
        assertEquals(35f, trainOffset(0.5f, 100f, 30f), 1e-4f)
        assertEquals(100f, trainOffset(3f, 100f, 30f), 1e-4f)
    }

    @Test
    fun theTrainSlowsDownWhileItIsInSight() {
        assertEquals(0f, trainGlide(0f), 1e-5f)
        assertEquals(0.5f, trainGlide(0.5f), 1e-5f)
        assertEquals(1f, trainGlide(1f), 1e-5f)
        assertEquals(1f, trainGlide(2f), 1e-5f)
        // Always moving forward.
        var last = -1f
        for (i in 0..100) {
            val now = trainGlide(i / 100f)
            assertTrue(now > last)
            last = now
        }
        // Slow in the middle, quick at the ends.
        val middle = trainGlide(0.51f) - trainGlide(0.49f)
        val start = trainGlide(0.02f) - trainGlide(0f)
        assertTrue(start > 3 * middle)
        // With a train half as long as its track it is wholly in sight (a third of the way to two
        // thirds) for close to half of the run, and only a quarter of the run goes to coming in.
        var whole = 0
        var enteredAt = -1
        for (i in 0..1000) {
            val p = trainGlide(i / 1000f)
            if (p >= 1f / 3 && p <= 2f / 3) whole++
            if (enteredAt < 0 && p >= 1f / 3) enteredAt = i
        }
        assertTrue(whole > 450)
        assertTrue(enteredAt in 200..260)
    }

    @Test
    fun theTrainNeverTakesMoreThanHalfItsTrack() {
        assertEquals(2f, trainUnit(2f, 1000f), 1e-5f)
        val unit = trainUnit(2f, 100f)
        assertTrue(unit < 2f)
        assertTrue(unit * 96f <= 50f + 1e-3f)
    }

    @Test
    fun aPageGrowsFromItsRowToItsOwnPlace() {
        val origin = floatArrayOf(20f, 300f, 200f, 60f)
        val page = floatArrayOf(0f, 100f, 400f, 600f)
        val start = growFrame(origin, page, 0f)
        assertEquals(0.5f, start[0], 1e-4f)
        assertEquals(0.1f, start[1], 1e-4f)
        assertEquals(20f, start[2], 1e-4f)
        assertEquals(200f, start[3], 1e-4f)
        val end = growFrame(origin, page, 1f)
        assertEquals(1f, end[0], 1e-4f)
        assertEquals(1f, end[1], 1e-4f)
        assertEquals(0f, end[2], 1e-4f)
        assertEquals(0f, end[3], 1e-4f)
    }

    @Test
    fun confettiShootsUpThenFallsAndFades() {
        val pieces = confettiPieces(CONFETTI_COUNT, 3, seed = 7)
        assertEquals(CONFETTI_COUNT, pieces.size)
        // The same seed throws the same burst.
        assertEquals(pieces[5].vx, confettiPieces(CONFETTI_COUNT, 3, seed = 7)[5].vx, 0f)
        for (piece in pieces) {
            assertTrue("every piece starts upwards", piece.vy < 0f)
            assertTrue(piece.colorIndex in 0 until 3)
            assertTrue(confettiPosition(piece, 0.15f).y < 0f)
            assertTrue("and has fallen below its start at the end", confettiPosition(piece, CONFETTI_SECONDS).y > 0f)
        }
        assertEquals(0f, confettiPosition(pieces[0], 0f).x, 1e-4f)
        assertEquals(1f, confettiAlpha(0.5f), 0f)
        assertEquals(0f, confettiAlpha(CONFETTI_SECONDS), 0f)
        assertTrue(confettiAlpha(CONFETTI_SECONDS - 0.25f) in 0.4f..0.6f)
    }

    @Test
    fun onlyAKnappOrSprintChangeIsCheeredOnceTheNextRideRuns() {
        assertTrue(changeMade(over = true, tightness = Tightness.Risky))
        assertTrue(changeMade(over = true, tightness = Tightness.VeryRisky))
        assertFalse(changeMade(over = false, tightness = Tightness.VeryRisky))
        assertFalse(changeMade(over = true, tightness = Tightness.Comfortable))
        assertFalse(changeMade(over = true, tightness = Tightness.Tight))
    }

    @Test
    fun anArrivalIsCelebratedOnce() {
        assertTrue(Celebrations.claim("test-trip-1"))
        assertFalse(Celebrations.claim("test-trip-1"))
        assertTrue(Celebrations.claim("test-trip-2"))
    }

    @Test
    fun theMarkedStopHopsLandsSquashedAndSettles() {
        val start = stopHop(0f)
        assertEquals(0f, start.lift, 1e-4f)
        assertEquals(0.72f, start.size, 1e-4f)
        assertEquals(0f, start.nameAlpha, 0f)
        val top = stopHop(240f)
        assertEquals(1f, top.lift, 1e-4f)
        assertEquals(1.08f, top.size, 1e-3f)
        val landed = stopHop(560f)
        assertEquals(0f, landed.lift, 0f)
        assertTrue(landed.size < 1f)
        val rest = stopHop(5_000f)
        assertEquals(1f, rest.size, 0f)
        assertEquals(0f, rest.lift, 0f)
        assertEquals(1f, rest.nameAlpha, 0f)
        // No jumps between the phases.
        var before = stopHop(0f)
        var t = 10f
        while (t < 1_000f) {
            val now = stopHop(t)
            assertTrue("size jumps at $t", kotlin.math.abs(now.size - before.size) < 0.08f)
            assertTrue("lift jumps at $t", kotlin.math.abs(now.lift - before.lift) < 0.1f)
            before = now
            t += 10f
        }
    }

    @Test
    fun theHopLastsUntilTheLastPositionHasPopped() {
        assertEquals(900L, stopHopMillis(0))
        assertEquals(900L, stopHopMillis(1))
        assertEquals(520L + 9 * POSITION_POP_STAGGER_MILLIS + POSITION_POP_MILLIS, stopHopMillis(10))
    }
}
