package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The chevrons flowing along a way (see LineArrows.kt). */
class ArrowFlowTest {
    @Test
    fun withoutFlowTheFirstChevronIsHalfASpacingIn() {
        assertEquals(50f, arrowStart(100f, 0f), 1e-4f)
    }

    @Test
    fun theFlowMovesItOnAndWraps() {
        assertEquals(80f, arrowStart(100f, 30f), 1e-4f)
        assertEquals(10f, arrowStart(100f, 60f), 1e-4f)
        assertEquals(50f, arrowStart(100f, 100f), 1e-4f)
        assertEquals(40f, arrowStart(100f, -10f), 1e-4f)
    }

    @Test
    fun theChevronsMoveInTheDirectionOfTheWay() {
        // A straight way to the right: every chevron lies the phase further along.
        val places = floatArrayOf(0f, 0f, 1000f, 0f)
        val still = ArrayList<Float>()
        val moved = ArrayList<Float>()
        walkArrows(places, 2, 1f, 100f, 1000f, 100f, 10f) { x, _, _, _ -> still.add(x) }
        walkArrows(places, 2, 1f, 100f, 1000f, 100f, 10f, phase = 20f) { x, _, _, _ -> moved.add(x) }
        assertEquals(still.size, moved.size)
        for (index in still.indices) assertEquals(still[index] + 20f, moved[index], 1e-3f)
    }

    @Test
    fun thePhaseStaysWithinOneSpacingAndRepeats() {
        val spacing = 96f
        val speed = 14f
        val period = (spacing / speed * 1000f).toLong()
        for (millis in longArrayOf(0L, 1L, 500L, 3_000L, 123_456L)) {
            val phase = arrowFlowPhase(millis, speed, spacing)
            assertTrue(phase >= 0f && phase < spacing)
            assertEquals(phase, arrowFlowPhase(millis + period, speed, spacing), 1e-3f)
        }
        assertEquals(0f, arrowFlowPhase(1_000L, 0f, spacing), 0f)
    }

    @Test
    fun itFlowsForwardAtItsSpeed() {
        // A second later the phase has gone on by the speed, within one spacing.
        val a = arrowFlowPhase(1_000L, 14f, 96f)
        val b = arrowFlowPhase(2_000L, 14f, 96f)
        assertEquals(14f, ((b - a) + 96f) % 96f, 0.2f)
    }
}
