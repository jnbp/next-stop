package io.github.jnbp.nextstop.ui

import java.time.OffsetDateTime
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.TransferComfort
import org.junit.Assert.assertEquals
import org.junit.Test

/** The chosen level never lets a tighter journey through, whatever a stale map of levels says. Made-up stops only. */
class JourneySelectionTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T10:00:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)
    private fun stop(id: String) = Stop("90000000$id", "Stop $id", 52.0, 13.0, null)
    private fun ride(from: Stop, to: Stop, leaves: Long, arrives: Long, plannedLeaves: Long = leaves, trip: String = "t$leaves") = Leg(
        "U1", "subway", false, from.name, to.name, at(leaves), at(plannedLeaves), at(arrives), at(arrives),
        null, null, null, false, fromPlace = from, toPlace = to, tripId = trip,
    )

    private val a = stop("1")
    private val hub = stop("2")
    private val b = stop("3")

    private val relaxed = Journey(listOf(ride(a, hub, 0, 10), ride(hub, b, 20, 30)), foundLevel = TransferComfort.Normal)
    private val normal = Journey(listOf(ride(a, hub, 1, 11), ride(hub, b, 17, 30)), foundLevel = TransferComfort.Normal)
    private val risky = Journey(listOf(ride(a, hub, 2, 12), ride(hub, b, 18, 30)), foundLevel = TransferComfort.Risky)
    // 2 minutes by the live times where the change needs 3: a sprint.
    private val veryRisky = Journey(listOf(ride(a, hub, 3, 13), ride(hub, b, 15, 30, plannedLeaves = 12)), foundLevel = TransferComfort.Normal)
    private val direct = Journey(listOf(ride(a, b, 4, 30)))
    private val stayOnBoard = Journey(listOf(ride(a, hub, 5, 15, trip = "x"), ride(hub, b, 15, 30, trip = "x")))
    private val all = listOf(relaxed, normal, risky, veryRisky, direct, stayOnBoard)

    @Test
    fun normalShowsNormalAndRelaxedOnly() {
        val selection = journeySelection(all, TransferComfort.Normal)
        assertEquals(listOf(relaxed, normal, direct, stayOnBoard), selection.shown)
        assertEquals(listOf(risky, veryRisky), selection.filtered)
        assertEquals(TransferComfort.Risky, selection.nextLevel)
        assertEquals(1, selection.nextCount)
    }

    @Test
    fun staleLevelsCannotLetATighterJourneyThrough() {
        // A map that files the very risky journey as normal (as a stale map once did) is not believed.
        val stale = all.associate { it.id to TransferComfort.Normal }
        val selection = journeySelection(all, TransferComfort.Normal, stale)
        assertEquals(false, selection.shown!!.contains(veryRisky))
        assertEquals(false, selection.shown!!.contains(risky))
    }

    @Test
    fun everyThresholdIsAdditive() {
        assertEquals(listOf(relaxed, direct, stayOnBoard), journeySelection(all, TransferComfort.Relaxed).shown)
        assertEquals(listOf(relaxed, normal, risky, direct, stayOnBoard), journeySelection(all, TransferComfort.Risky).shown)
        assertEquals(all, journeySelection(all, TransferComfort.VeryRisky).shown)
    }
}
