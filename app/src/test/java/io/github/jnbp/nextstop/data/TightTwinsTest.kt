package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A "knapp" journey beside a looser one on the same lines and with the same arrival is left out (#130). Made-up stops only. */
class TightTwinsTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T10:00:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)
    private fun stop(id: String) = Stop("90000000$id", "Stop $id", 52.0, 13.0, null)
    private fun ride(from: Stop, to: Stop, leaves: Long, arrives: Long, line: String = "U1", trip: String = "t$leaves", cancelled: Boolean = false) = Leg(
        line, "subway", false, from.name, to.name, at(leaves), at(leaves), at(arrives), at(arrives),
        null, null, null, cancelled, fromPlace = from, toPlace = to, tripId = trip,
    )

    private val a = stop("1")
    private val hub = stop("2")
    private val b = stop("3")

    // The same second ride: one comes early to the change, the other leaves later and has no time to spare.
    private val roomy = Journey(listOf(ride(a, hub, 0, 10), ride(hub, b, 20, 30, line = "U2", trip = "x")))
    private val tight = Journey(listOf(ride(a, hub, 7, 17), ride(hub, b, 20, 30, line = "U2", trip = "x")))

    @Test
    fun fixtureIsTightBesideALooserOne() {
        assertTrue(journeyComfort(tight).ordinal >= TransferComfort.Risky.ordinal)
        assertTrue(journeyComfort(roomy).ordinal < journeyComfort(tight).ordinal)
    }

    @Test
    fun theTightTwinGoes() {
        assertEquals(listOf(roomy), withoutTightTwins(listOf(tight, roomy)))
        assertEquals(listOf(roomy), withoutTightTwins(listOf(roomy, tight)))
    }

    @Test
    fun anEarlierArrivalStays() {
        val earlier = Journey(listOf(ride(a, hub, 7, 17), ride(hub, b, 20, 29, line = "U2", trip = "y")))
        val list = listOf(earlier, roomy)
        assertEquals(list, withoutTightTwins(list))
    }

    @Test
    fun otherLinesStay() {
        val other = Journey(listOf(ride(a, hub, 7, 17, line = "U3"), ride(hub, b, 20, 30, line = "U2", trip = "x")))
        val list = listOf(other, roomy)
        assertEquals(list, withoutTightTwins(list))
    }

    @Test
    fun aCancelledLooserOneDoesNotDecide() {
        val cancelled = Journey(listOf(ride(a, hub, 0, 10, cancelled = true), ride(hub, b, 20, 30, line = "U2", trip = "x")))
        val list = listOf(tight, cancelled)
        assertEquals(list, withoutTightTwins(list))
    }

    @Test
    fun aSwappedJourneyStays() {
        val swapped = tight.copy(split = JourneySplit(headLegs = 1, headToken = null, tailToken = null))
        val list = listOf(swapped, roomy)
        assertEquals(list, withoutTightTwins(list))
    }
}
