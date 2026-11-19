package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The chevrons flow while the map is used, slow to rest after a while and never jump. */
class ArrowFlowRunTest {
    private fun run(flow: ArrowFlowRun, from: Long, to: Long, onStep: (Float) -> Unit = {}) {
        var t = from
        while (t <= to) {
            onStep(flow.step(t, t))
            t += 33L
        }
    }

    @Test
    fun flowsAtFullPaceAfterTheFadeIn() {
        val flow = ArrowFlowRun(spacing = 300f, speed = 30f)
        flow.active(0L)
        run(flow, 0L, 1_000L)
        assertEquals(1f, flow.pace, 0f)
        val before = flow.phase
        run(flow, 1_033L, 2_002L)
        assertEquals(before + 30f, flow.phase, 1.5f)
    }

    @Test
    fun comesToRestEightSecondsAfterTheLastMoveWithoutAJump() {
        val flow = ArrowFlowRun(spacing = 1_000f, speed = 30f)
        flow.active(0L)
        var last = 0f
        var largest = 0f
        run(flow, 0L, ARROW_FLOW_REST_MILLIS + 2_000L) { phase ->
            largest = maxOf(largest, phase - last)
            last = phase
        }
        assertTrue(flow.resting(ARROW_FLOW_REST_MILLIS + 2_000L))
        assertEquals(0f, flow.pace, 0f)
        // Never more than one step at full pace.
        assertTrue("$largest", largest <= 30f * 33f / 1000f + 1e-3f)
    }

    @Test
    fun aMoveWakesItAndThePaceRisesGently() {
        val flow = ArrowFlowRun(spacing = 1_000f, speed = 30f)
        flow.active(0L)
        run(flow, 0L, 10_000L)
        assertTrue(flow.resting(10_000L))
        flow.active(20_000L)
        assertFalse(flow.resting(20_000L))
        flow.pause()
        flow.step(20_000L, 20_000L)
        flow.step(20_033L, 20_033L)
        assertTrue(flow.pace in 0.01f..0.1f)
    }

    @Test
    fun aPauseDoesNotCatchUp() {
        val flow = ArrowFlowRun(spacing = 1_000f, speed = 30f)
        flow.active(0L)
        run(flow, 0L, 1_000L)
        val before = flow.phase
        flow.active(5_000L)
        flow.pause()
        flow.step(5_000L, 5_000L)
        assertEquals(before, flow.phase, 0f)
    }

    @Test
    fun aWayUnderTheCardsIsHidden() {
        val way = floatArrayOf(10f, 700f, 300f, 760f, 380f, 900f)
        // Free room 0..400 × 0..600: the way lies below it.
        assertFalse(wayInRoom(way, 3, 0f, 0f, 400f, 600f))
        assertTrue(wayInRoom(way, 3, 0f, 0f, 400f, 720f))
        // A stretch across the room with both ends outside counts.
        assertTrue(wayInRoom(floatArrayOf(-50f, 100f, 450f, 100f), 2, 0f, 0f, 400f, 600f))
    }
}
