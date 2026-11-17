package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two columns that only differ in a tighter change are one choice: the one with more room stays.
 * Made-up stops only.
 */
class DominatedJourneysTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T19:00:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)
    private fun stop(id: String) = Stop("90000000$id", "Stop $id", 52.0, 13.0, null)
    private fun ride(line: String, product: String, from: String, to: String, leaves: Long, arrives: Long, trip: String) = Leg(
        line, product, false, "Stop $from", "Stop $to", at(leaves), at(leaves), at(arrives), at(arrives),
        null, null, null, false, fromPlace = stop(from), toPlace = stop(to), tripId = trip,
    )

    // M41 at 19:32, S2, U7, at the destination 20:03; the two S2 trips leave 19:45 and 19:46.
    private val bus = ride("M41", "bus", "1", "2", 32, 42, "m41")
    private val u7 = ride("U7", "subway", "3", "4", 56, 63, "u7")
    private val tight = Journey(listOf(bus, ride("S2", "suburban", "2", "3", 45, 51, "s2a"), u7))
    private val roomy = Journey(listOf(bus, ride("S2", "suburban", "2", "3", 46, 52, "s2b"), u7))

    @Test
    fun theTighterTwinGoes() {
        assertTrue(dominates(roomy, tight))
        assertFalse(dominates(tight, roomy))
        assertEquals(listOf(roomy), withoutDominated(listOf(tight, roomy)))
        assertEquals(listOf(roomy), withoutDominated(listOf(roomy, tight)))
    }

    @Test
    fun otherLinesStay() {
        // The same times on other lines are another way to go: both stay.
        val other = Journey(listOf(ride("M41", "bus", "1", "2", 32, 42, "m41"), ride("S46", "suburban", "2", "3", 45, 51, "s46"), u7))
        assertEquals(listOf(other, roomy), withoutDominated(listOf(other, roomy)))
    }

    @Test
    fun aLaterArrivalOrAnEarlierStartStays() {
        // Leaves earlier and gets there earlier: a choice of its own.
        val sooner = Journey(
            listOf(ride("M41", "bus", "1", "2", 22, 32, "m41x"), ride("S2", "suburban", "2", "3", 36, 42, "s2x"), ride("U7", "subway", "3", "4", 46, 53, "u7x")),
        )
        assertFalse(dominates(roomy, sooner))
        // Leaving earlier only to wait longer at a change, for the same arrival, is no choice.
        val waitLonger = Journey(listOf(ride("M41", "bus", "1", "2", 22, 32, "m41x"), ride("S2", "suburban", "2", "3", 46, 52, "s2b"), u7))
        assertTrue(dominates(roomy, waitLonger))
        // Arrives later with more room: a choice of its own.
        val later = Journey(listOf(bus, ride("S2", "suburban", "2", "3", 46, 52, "s2b"), ride("U7", "subway", "3", "4", 61, 68, "u7b")))
        assertFalse(dominates(later, tight))
        assertEquals(3, withoutDominated(listOf(sooner, tight, later)).size)
    }

    @Test
    fun aCancelledOrSwappedJourneyNeverDecides() {
        val cancelled = roomy.copy(legs = roomy.legs.mapIndexed { i, leg -> if (i == 1) leg.copy(cancelled = true) else leg })
        assertFalse(dominates(cancelled, tight))
        assertEquals(2, withoutDominated(listOf(tight, cancelled)).size)
    }

    @Test
    fun twinsWithTheSameRoomKeepTheFirst() {
        val twin = Journey(listOf(bus, ride("S2", "suburban", "2", "3", 45, 51, "s2c"), u7))
        assertEquals(listOf(tight), withoutDominated(listOf(tight, twin)))
    }
}
