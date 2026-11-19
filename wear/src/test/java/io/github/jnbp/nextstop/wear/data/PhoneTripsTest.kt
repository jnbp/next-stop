package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The state of the phone's trip the watch takes, with items of more than one phone, and a tolerant read. */
class PhoneTripsTest {
    private val minute = 60_000L
    private val now = 1_000 * minute

    private fun running(node: String, updated: Long = now - minute, key: String = "k-$node") =
        TripSnapshot(active = true, kind = StepKind.Board, fromPhone = true, tripKey = key, started = now - 2 * minute, updated = updated, arrival = now + 30 * minute, node = node)

    private fun ended(node: String, updated: Long = now) =
        TripSnapshot(active = false, fromPhone = true, updated = updated, node = node)

    @Test
    fun theRunningTripWinsOverAnEndedOneOfAnotherPhone() {
        val trip = running("a")
        // Newer, but ended: the item of a second phone (or an old pairing) must not hide the trip.
        assertEquals(trip, choosePhoneTrip(listOf(trip, ended("b")), now))
        assertEquals(trip, choosePhoneTrip(listOf(ended("b"), trip), now))
    }

    @Test
    fun aStaleTripLosesToARunningOne() {
        val stale = running("b", updated = now - 10 * minute)
        val live = running("a")
        assertEquals(live, choosePhoneTrip(listOf(stale, live), now))
    }

    @Test
    fun withoutARunningTripTheNewestStateIsTaken() {
        val older = ended("a", updated = now - 5 * minute)
        val newer = ended("b", updated = now)
        assertEquals(newer, choosePhoneTrip(listOf(older, newer), now))
        assertNull(choosePhoneTrip(emptyList(), now))
    }

    @Test
    fun onlyTheSamePhoneEndsTheTripShown() {
        val shown = running("a")
        assertTrue(endsShownTrip(shown, ended("a"), now))
        assertFalse(endsShownTrip(shown, ended("b"), now))
        // Unknown nodes: as before, any end counts.
        assertTrue(endsShownTrip(shown.copy(node = null), ended("b"), now))
        assertTrue(endsShownTrip(shown, ended("b").copy(node = null), now))
        // A stale trip shown ends with any state.
        assertTrue(endsShownTrip(running("a", updated = now - 10 * minute), ended("b"), now))
        // Nothing of the phone shown: nothing to end.
        assertFalse(endsShownTrip(TripSnapshot.NONE, ended("a"), now))
        assertFalse(endsShownTrip(shown.copy(fromPhone = false), ended("a"), now))
    }

    @Test
    fun aBrokenPartDoesNotDropTheTrip() {
        // "next" and "plan" of a kind this watch does not read, a new key it does not know.
        val json = """
            {"active":true,"kind":"Board","line":"U7","product":"subway","place":"Stop A","time":${now + 5 * minute},
             "arrival":${now + 30 * minute},"destination":"Stop B","fromPhone":true,"tripKey":"t","started":${now - minute},
             "updated":$now,"next":"not an object","plan":{"oops":1},"stops":[1,2,{"name":"x","time":5}],
             "map":{"fromLat":"x"},"waiting":true,"standing":false,"missed":true,"somethingNew":{"a":[1]}}
        """.trimIndent()
        val trip = TripSnapshot.parse(json)
        assertTrue(trip.active)
        assertEquals(StepKind.Board, trip.kind)
        assertEquals("Stop A", trip.place)
        assertNull(trip.next)
        assertTrue(trip.plan.isEmpty())
        assertEquals(1, trip.stops.size)
        assertNull(trip.map)
        assertTrue(trip.waiting)
        assertFalse(trip.standing)
        assertTrue(trip.missed)
    }

    @Test
    fun theNodeAndTheRideStateSurviveTheStore() {
        val trip = running("node-1").copy(waiting = true, standing = true, missed = true)
        assertEquals(trip, TripSnapshot.parse(trip.toJson()))
        assertEquals(TripSnapshot.NONE, TripSnapshot.parse("not json"))
    }
}
