package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The loading train passing its platform without stopping (see trainPass in TrainPace.kt). */
class TrainPassTest {
    private val steps = 1000

    private fun speedAt(i: Int): Float = (trainPass((i + 1f) / steps) - trainPass(i.toFloat() / steps)) * steps

    @Test
    fun itStartsOutOfSightAndEndsOutOfSight() {
        assertEquals(0f, trainPass(0f), 1e-6f)
        assertEquals(1f, trainPass(1f), 1e-6f)
        assertEquals(0f, trainPass(-1f), 1e-6f)
        assertEquals(1f, trainPass(2f), 1e-6f)
        // At 0 the train's right edge is at the left edge of the track; at 1 its left edge is at the right edge.
        assertEquals(0f, trainOffset(trainPass(0f), 300f, 100f) + 100f, 1e-3f)
        assertEquals(300f, trainOffset(trainPass(1f), 300f, 100f), 1e-3f)
    }

    @Test
    fun itIsInTheMiddleOfTheTrackAtHalfTheRun() {
        assertEquals(0.5f, trainPass(0.5f), 1e-4f)
        assertEquals(100f, trainOffset(trainPass(0.5f), 300f, 100f), 0.05f)
    }

    @Test
    fun itAlwaysMovesOn() {
        var before = trainPass(0f)
        for (i in 1..steps) {
            val now = trainPass(i.toFloat() / steps)
            assertTrue("goes back or stands at ${i.toFloat() / steps}", now > before)
            before = now
        }
    }

    @Test
    fun itNeverStops() {
        val entry = speedAt(0)
        assertTrue(entry > 1f)
        for (i in 0 until steps) assertTrue("too slow at ${i.toFloat() / steps}", speedAt(i) >= 0.44f * entry)
    }

    @Test
    fun itIsSlowestInTheMiddle() {
        var slowest = 0
        for (i in 0 until steps) if (speedAt(i) < speedAt(slowest)) slowest = i
        assertEquals(0.5f, (slowest + 0.5f) / steps, 0.01f)
        // About 0.45 of the speed at the ends (#261: a glide, no stop-and-go), and as fast leaving as coming in.
        assertEquals(TRAIN_SLOWEST_SHARE, speedAt(steps / 2) / speedAt(0), 0.01f)
        assertEquals(speedAt(0), speedAt(steps - 1), 1e-3f)
    }

    @Test
    fun itsSpeedIsTheBell() {
        assertEquals(1f, trainSpeed(0f), 1e-6f)
        assertEquals(1f, trainSpeed(0.5f - TRAIN_SLOW_WIDTH / 2f - 0.01f), 1e-6f)
        assertEquals(TRAIN_SLOWEST_SHARE, trainSpeed(0.5f), 1e-6f)
        assertEquals(1f, trainSpeed(1f), 1e-6f)
    }

    @Test
    fun oneRunTakesTwoPointSevenSeconds() {
        assertEquals(2_700, TRAIN_RUN_MILLIS)
    }

    @Test
    fun itBrakesAndStartsGently() {
        // From full speed down to the slowest takes more than a third of the run (#124) ...
        assertTrue(TRAIN_SLOW_WIDTH / 2f > 0.33f)
        // ... so the speed changes by at most about 2.5 times the speed at the ends per run
        // (3.9 with the slowest share of 0.14 before #261, 6.4 with the 0.42 wide bell), never in a jolt.
        val dt = 1e-4f
        var t = 0f
        while (t < 1f) {
            val change = kotlin.math.abs(trainSpeed(t + dt) - trainSpeed(t)) / dt
            assertTrue("abrupt at $t: $change", change <= 2.5f)
            t += dt
        }
    }
}
