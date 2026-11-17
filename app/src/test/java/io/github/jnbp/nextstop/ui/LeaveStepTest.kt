package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Before a journey that starts on foot the next step is leaving, at the start of the walk, not
 * the departure of the first ride. Made-up places: a 9 min walk to a bus at 05:16.
 */
class LeaveStepTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-01-01T${clock}:00+01:00")

    private fun walk(from: String, to: String, leaves: String, arrives: String) =
        Leg(null, "walking", true, from, to, at(leaves), at(leaves), at(arrives), at(arrives), null, null, null, false)

    private fun ride(line: String, from: String, to: String, leaves: String, arrives: String) =
        Leg(line, "bus", false, from, to, at(leaves), at(leaves), at(arrives), at(arrives), null, null, null, false, tripId = "$line@$leaves")

    private val bus = ride("M46", "Stop A", "Stop B", "05:16", "05:40")
    private val byFoot = Journey(listOf(walk("Home", "Stop A", "05:07", "05:16"), bus, walk("Stop B", "Office", "05:40", "05:45")))

    @Test
    fun beforeTheWalkTheStepIsLeaving() {
        val step = tripStep(byFoot, at("04:50"))
        assertEquals(StepKind.Board, step.kind)
        assertTrue(step.leave)
        assertEquals(at("05:07"), step.time)
        assertEquals("M46", step.leg?.line)
    }

    @Test
    fun onceTheLeaveTimeHasPassedTheStepIsTheRide() {
        val step = tripStep(byFoot, at("05:07"))
        assertEquals(StepKind.Board, step.kind)
        assertFalse(step.leave)
        assertEquals(at("05:16"), step.time)
    }

    @Test
    fun aJourneyStartingWithTheRideHasNoLeaveStep() {
        val direct = Journey(listOf(bus))
        val step = tripStep(direct, at("05:00"))
        assertFalse(step.leave)
        assertEquals(at("05:16"), step.time)
        assertNull(leaveTime(direct.legs, 0))
    }

    @Test
    fun aWalkAtAChangeIsNoLeaveStep() {
        val first = ride("100", "Stop C", "Stop D", "04:50", "05:05")
        val changing = Journey(listOf(walk("Home", "Stop C", "04:45", "04:50"), first, walk("Stop D", "Stop A", "05:05", "05:12"), bus))
        val step = tripStep(changing, at("05:06"))
        assertFalse(step.leave)
        assertEquals("M46", step.leg?.line)
        assertEquals(at("05:16"), step.time)
        // Before the very start the first ride's walk counts.
        assertTrue(tripStep(changing, at("04:30")).leave)
    }

    @Test
    fun aWalkWithoutTimeOfItsOwnIsNoLeaveStep() {
        val instant = Journey(listOf(walk("Home", "Stop A", "05:16", "05:16"), bus))
        assertNull(leaveTime(instant.legs, 1))
        assertFalse(tripStep(instant, at("05:00")).leave)
    }

    @Test
    fun aWalkOfNoDistanceIsNoLeaveStep() {
        val inside = walk("Stop A entrance", "Stop A", "05:07", "05:16").copy(walkMeters = 0)
        val station = Journey(listOf(inside, bus))
        assertNull(leaveTime(station.legs, 1))
        assertFalse(tripStep(station, at("04:50")).leave)
        assertEquals(at("05:16"), tripStep(station, at("04:50")).time)
        // An unknown distance is a real walk.
        assertTrue(tripStep(Journey(listOf(inside.copy(walkMeters = null), bus)), at("04:50")).leave)
    }
}
