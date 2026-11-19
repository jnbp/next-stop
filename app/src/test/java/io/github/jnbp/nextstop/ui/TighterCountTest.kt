package io.github.jnbp.nextstop.ui

import java.time.OffsetDateTime
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.TIGHTER_EXTRA_CAP
import io.github.jnbp.nextstop.data.TransferComfort
import io.github.jnbp.nextstop.data.journeyComfort
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #57: "Sprint +39" over a chart of near-identical columns. The next level offers only the
 * journeys that are new after grouping, and the tightest level at most a few. Made-up stops.
 */
class TighterCountTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T06:30:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)
    private fun stop(id: String) = Stop("90000000$id", "Stop $id", 52.0, 13.0, null)
    private fun ride(line: String, product: String, from: Stop, to: Stop, leaves: Long, arrives: Long, plannedLeaves: Long = leaves, trip: String = "$line-$leaves") = Leg(
        line, product, false, from.name, to.name, at(leaves), at(plannedLeaves), at(arrives), at(arrives),
        null, null, null, false, fromPlace = from, toPlace = to, tripId = trip,
    )

    private val a = stop("1")
    private val hub = stop("2")
    private val b = stop("3")

    // Relaxed: ten minutes to change.
    private val relaxed = Journey(listOf(ride("U3", "subway", a, hub, 0, 10), ride("U3", "subway", hub, b, 20, 30, trip = "other")))
    // A sprint: the second train leaves two minutes after the first arrives, live (planned three minutes earlier).
    private fun sprint(n: Long) = Journey(listOf(ride("U3", "subway", a, hub, 3 + n, 13 + n), ride("U9", "subway", hub, b, 15 + n, 25 + n, plannedLeaves = 12 + n)))

    @Test
    fun theTightestLevelOffersOnlyAFewNewJourneys() {
        val sprints = (0L until 10L).map { sprint(it * 4) }
        sprints.forEach { assertEquals(TransferComfort.VeryRisky, journeyComfort(it)) }
        val all = listOf(relaxed) + sprints
        val atRisky = journeySelection(all, TransferComfort.Risky)
        assertEquals(listOf(relaxed), atRisky.shown)
        assertEquals(TransferComfort.VeryRisky, atRisky.nextLevel)
        assertEquals(TIGHTER_EXTRA_CAP, atRisky.nextCount)
        val atSprint = journeySelection(all, TransferComfort.VeryRisky)
        assertEquals(listOf(relaxed) + sprints.take(TIGHTER_EXTRA_CAP), atSprint.shown)
    }

    @Test
    fun aCopyOfAShownJourneyIsNoNews() {
        // The same rides as the relaxed one, only with a walk before: a copy.
        val copy = relaxed.copy(legs = listOf(Leg(null, "", true, "Home", a.name, at(-5), at(-5), at(0), at(0), null, null, null, false, toPlace = a)) + relaxed.legs)
        val selection = journeySelection(listOf(relaxed, copy, sprint(0)), TransferComfort.Risky)
        assertEquals(1, selection.shown!!.size)
        assertEquals(1, selection.nextCount)
    }
}
