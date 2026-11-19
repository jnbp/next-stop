package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The companion opens by itself only for a trip of the phone that has just begun. */
class CompanionStartTest {
    private val minute = 60_000L
    private val now = 1_000 * minute

    private fun phoneTrip(key: String?, started: Long = now - minute, kind: StepKind = StepKind.Board) =
        TripSnapshot(active = true, kind = kind, fromPhone = true, tripKey = key, started = started, updated = now)

    @Test
    fun aNewTripOfThePhoneOpensIt() {
        assertTrue(startedOnPhone(TripSnapshot.NONE, phoneTrip("a"), now))
        // An older phone app sends no start time and no key: still new when nothing ran before.
        assertTrue(startedOnPhone(TripSnapshot.NONE, phoneTrip(null, started = 0), now))
        // Another trip of the phone replaces the one shown.
        assertTrue(startedOnPhone(phoneTrip("a"), phoneTrip("b"), now))
    }

    @Test
    fun theSameTripAgainDoesNot() {
        assertFalse(startedOnPhone(phoneTrip("a"), phoneTrip("a"), now))
        assertFalse(startedOnPhone(phoneTrip(null), phoneTrip(null), now))
    }

    @Test
    fun aTripOfTheWatchTakenOverByThePhoneDoesNot() {
        val own = TripSnapshot(active = true, kind = StepKind.Board, fromPhone = false, tripKey = "w")
        assertFalse(startedOnPhone(own, phoneTrip("a"), now))
    }

    @Test
    fun aTripRunningForAWhileOrOverDoesNot() {
        assertFalse(startedOnPhone(TripSnapshot.NONE, phoneTrip("a", started = now - 20 * minute), now))
        assertFalse(startedOnPhone(TripSnapshot.NONE, phoneTrip("a", kind = StepKind.Arrived), now))
        assertFalse(startedOnPhone(TripSnapshot.NONE, TripSnapshot.NONE, now))
    }
}
