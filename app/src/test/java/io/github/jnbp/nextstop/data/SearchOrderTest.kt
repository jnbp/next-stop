package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The connections of a search: by the time to set off, each once, the same order from any page. */
class SearchOrderTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T17:50:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)

    /** A ride of [line] (a walk when null), [late] minutes behind its timetable. */
    private fun leg(line: String?, leaves: Long, arrives: Long, late: Long = 0) = Leg(
        line, if (line == null) "walking" else "subway", line == null, "Alexanderplatz", "Zoologischer Garten",
        at(leaves + late), at(leaves), at(arrives + late), at(arrives), null, null, null, false,
    )

    @Test
    fun theTimeToSetOffCountsAWalkToTheFirstStop() {
        // The card of the walk-first journey says 17:50 (set off), the other one 17:55.
        val walkFirst = Journey(listOf(leg(null, 0, 8), leg("U2", 8, 30)))
        val rideFirst = Journey(listOf(leg("U5", 5, 25)))
        assertEquals(listOf(walkFirst, rideFirst), listOf(rideFirst, walkFirst).sortedWith(searchOrder))
    }

    @Test
    fun aDelayedDepartureSortsByItsLiveTime() {
        // Planned 17:52, leaves 17:58: behind the U5 at 17:55, as the card shows it.
        val late = Journey(listOf(leg("U2", 2, 20, late = 6)))
        val onTime = Journey(listOf(leg("U5", 5, 25)))
        assertEquals(listOf(onTime, late), listOf(late, onTime).sortedWith(searchOrder))
    }

    @Test
    fun atTheSameDepartureArrivalThenRidesThenWalking() {
        val slow = Journey(listOf(leg("U1", 3, 30)))
        val fast = Journey(listOf(leg("U8", 3, 18)))
        val twoRides = Journey(listOf(leg("U2", 3, 10), leg("U6", 12, 20)))
        val oneRide = Journey(listOf(leg("U8", 3, 20)))
        val walking = Journey(listOf(leg("U5", 3, 15), leg(null, 15, 20)))
        val sorted = listOf(slow, twoRides, walking, oneRide, fast).sortedWith(searchOrder)
        assertEquals(listOf(fast, oneRide, walking, twoRides, slow), sorted)
    }

    @Test
    fun theOrderDoesNotDependOnTheOrderOfThePages() {
        // Equal in time, rides and walking: only the id tells them apart, the same way every time.
        val a = Journey(listOf(leg("U2", 3, 20)))
        val b = Journey(listOf(leg("U5", 3, 20)))
        val c = Journey(listOf(leg("U8", 3, 20)))
        val expected = listOf(a, b, c).sortedWith(searchOrder)
        listOf(listOf(a, b, c), listOf(c, b, a), listOf(b, a, c), listOf(c, a, b)).forEach {
            assertEquals(expected, it.sortedWith(searchOrder))
        }
    }

    @Test
    fun timesCompareAsInstantsWhateverTheirOffset() {
        val berlin = Journey(listOf(leg("U2", 5, 20)))
        // 16:53 UTC is 17:53 in Berlin: before 17:55.
        val utcLeg = leg("U5", 0, 20).copy(
            departure = OffsetDateTime.parse("2026-01-01T16:53:00Z"),
            plannedDeparture = OffsetDateTime.parse("2026-01-01T16:53:00Z"),
        )
        val utc = Journey(listOf(utcLeg))
        assertEquals(listOf(utc, berlin), listOf(berlin, utc).sortedWith(searchOrder))
    }

    @Test
    fun aNewPageMergesInOrderAndEachConnectionOnce() {
        val first = Journey(listOf(leg("U2", 2, 20)))
        val second = Journey(listOf(leg("U5", 6, 25)))
        val shown = listOf(first, second)
        // The later page starts with one already shown: the same ride, its walk timed otherwise.
        val again = Journey(listOf(leg(null, 1, 2), leg("U5", 6, 25)))
        val third = Journey(listOf(leg("U8", 4, 22)))
        val merged = mergeSearchPages(shown, listOf(again, third))
        assertEquals(listOf(first, third, second), merged)
        // The copy that was shown stays (its pick and loaded stops go with it).
        assertSame(second, merged.last())
    }

    @Test
    fun earlierAndLaterPagesGiveTheSameList() {
        val a = Journey(listOf(leg("U2", 0, 20)))
        val b = Journey(listOf(leg("U5", 5, 25)))
        val c = Journey(listOf(leg("U8", 10, 30)))
        val d = Journey(listOf(leg("U1", 15, 35)))
        val fromLater = mergeSearchPages(mergeSearchPages(emptyList(), listOf(a, b)), listOf(b, c, d))
        val fromEarlier = mergeSearchPages(mergeSearchPages(emptyList(), listOf(c, d)), listOf(a, b, c))
        assertEquals(listOf(a, b, c, d), fromLater)
        assertEquals(fromLater, fromEarlier)
    }

    @Test
    fun aRefreshThatMovesAJourneyPastAnotherReorders() {
        val u2 = Journey(listOf(leg("U2", 2, 20)))
        val u5 = Journey(listOf(leg("U5", 5, 25)))
        val list = mergeSearchPages(emptyList(), listOf(u2, u5))
        // Live data: the U2 now leaves 6 minutes late, after the U5.
        val fresh = Journey(listOf(leg("U2", 2, 20, late = 6)))
        assertEquals(u2.id, fresh.id)
        val refreshed = list.map { if (it.id == fresh.id) fresh else it }.sortedWith(searchOrder)
        assertEquals(listOf(u5, fresh), refreshed)
    }
}
