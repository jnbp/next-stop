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
    private val normal = Journey(listOf(ride(a, hub, 1, 11), ride(hub, b, 17, 31)), foundLevel = TransferComfort.Normal)
    // 3 minutes where the change needs 3: knapp by the change itself (not by the search that found it).
    private val risky = Journey(listOf(ride(a, hub, 2, 12), ride(hub, b, 15, 32, trip = "t15r")), foundLevel = TransferComfort.Risky)
    // 2 minutes by the live times where the change needs 3: a sprint.
    private val veryRisky = Journey(listOf(ride(a, hub, 3, 13), ride(hub, b, 15, 33, plannedLeaves = 12)), foundLevel = TransferComfort.Normal)
    private val direct = Journey(listOf(ride(a, b, 4, 34)))
    private val stayOnBoard = Journey(listOf(ride(a, hub, 5, 15, trip = "x"), ride(hub, b, 15, 35, trip = "x")))
    // Every journey arrives at another time: none makes another pointless (see withoutDominated).
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

    @Test
    fun aTighterTwinIsNoColumnOfItsOwn() {
        // The same U1 and change stop, the second ride a minute later on another trip: more room, same arrival.
        // (The fixture's own journeys arrive at different times, so none of them goes.)
        val roomy = Journey(listOf(ride(a, hub, 1, 11), ride(hub, b, 18, 31, trip = "t18b")), foundLevel = TransferComfort.Normal)
        val selection = journeySelection(listOf(normal, roomy), TransferComfort.VeryRisky)
        assertEquals(listOf(roomy), selection.shown)
        // Journeys of other times stay.
        assertEquals(all, journeySelection(all, TransferComfort.VeryRisky).shown)
    }

    @Test
    fun aKnappTwinWithTheSameArrivalIsLeftOut() {
        // #130: the same lines and arrival as the relaxed journey, leaving later with no time to spare.
        val late = Journey(listOf(ride(a, hub, 7, 17), ride(hub, b, 20, 30)), foundLevel = TransferComfort.Risky)
        val selection = journeySelection(listOf(relaxed, late), TransferComfort.VeryRisky)
        assertEquals(listOf(relaxed), selection.shown)
        assertEquals(emptyList<Journey>(), selection.filtered)
    }

    @Test
    fun aCancelledWayToTheSameTrainIsNoColumnBesideTheRunningOne() {
        // #233: the same long-distance run boarded at two stations; at the second it skips its stop.
        val example = Stop("x:hbf", "Example Hbf", 50.0, 10.0, null)
        val south = Stop("x:sued", "Example Süd", 50.0, 10.0, null)
        val town = Stop("x:ziel", "Zielstadt Bahnhof", 49.0, 9.0, null)
        fun train(from: Stop, leaves: Long, cancelled: Boolean) = Leg(
            "ICE 277", "express", false, from.name, town.name, at(leaves), at(leaves), at(480), at(480),
            null, null, null, cancelled, fromPlace = from, toPlace = town, tripId = "ice",
        )
        val running = Journey(listOf(ride(a, example, 0, 15, trip = "bus1"), train(example, 20, false)))
        val skipped = Journey(listOf(ride(a, south, 6, 22, trip = "bus2"), train(south, 28, true)))
        assertEquals(listOf(running), journeySelection(listOf(running, skipped), TransferComfort.Risky).shown)
    }
}
