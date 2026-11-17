package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Before a journey that starts on foot the watch counts down to leaving, not to the ride. Made-up
 * places: a 9 min walk to a bus that leaves at minute 16.
 */
class LeaveSnapshotTest {
    private val minute = 60_000L
    private fun walk(from: String, to: String, leaves: Long, arrives: Long) =
        Leg(null, "walking", true, from, to, leaves * minute, arrives * minute, 0, 0, null, null, false)
    private val bus = Leg("M46", "bus", false, "Stop A", "Stop B", 16 * minute, 40 * minute, 2, 2, null, null, false)
    private val journey = Journey(listOf(walk("Home", "Stop A", 7, 16), bus, walk("Stop B", "Office", 40, 45)), null)

    @Test
    fun beforeTheWalkTheWatchCountsToLeaving() {
        val snapshot = snapshotOf(journey, 1 * minute)
        assertEquals(StepKind.Board, snapshot.kind)
        assertTrue(snapshot.leave)
        assertEquals(7 * minute, snapshot.time)
        assertEquals(16 * minute, snapshot.departure)
        // The ride's own delay stays, for the "+2" of the depart page.
        assertEquals(2, snapshot.delayMinutes)
    }

    @Test
    fun afterTheLeaveTimeTheWatchCountsToTheRide() {
        val snapshot = snapshotOf(journey, 7 * minute)
        assertFalse(snapshot.leave)
        assertEquals(16 * minute, snapshot.time)
    }

    @Test
    fun theLeaveStepSurvivesTheStore() {
        val again = TripSnapshot.parse(snapshotOf(journey, 1 * minute).toJson())
        assertTrue(again.leave)
        assertEquals(7 * minute, again.time)
        assertEquals(16 * minute, again.departure)
        assertFalse(TripSnapshot.parse(snapshotOf(journey, 8 * minute).toJson()).leave)
    }

    @Test
    fun onTheWireTimeStaysTheRidesDeparture() {
        val json = org.json.JSONObject(snapshotOf(journey, 1 * minute).toJson())
        assertEquals(16 * minute, json.getLong("time"))
        assertEquals(7 * minute, json.getLong("leaveAt"))
        // A watch that knows nothing of leaving reads boarding at the ride's time.
        json.remove("leaveAt")
        val old = TripSnapshot.parse(json.toString())
        assertFalse(old.leave)
        assertEquals(16 * minute, old.time)
    }

    @Test
    fun aWalkWithinAStationIsNoLeaveStep() {
        val inside = Journey(listOf(walk("Stop A entrance", "Stop A", 7, 16).copy(inStation = true), bus), null)
        val snapshot = snapshotOf(inside, 1 * minute)
        assertFalse(snapshot.leave)
        assertEquals(16 * minute, snapshot.time)
    }
}
