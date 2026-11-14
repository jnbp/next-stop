package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stops of the departures tab (see DepartureStops.kt) and "Wann los?" over several of them. */
class DepartureStopsTest {
    private val here = 52.5000 to 13.3000
    private val at = OffsetDateTime.parse("2026-11-14T10:00:00+01:00")

    // A stop [north] metres due north of [here], with a distance from the provider that need not fit.
    private fun stop(id: String, north: Double, providerDistance: Int? = 1) =
        Stop(id, "Stop $id", here.first + north / 111_320.0, here.second, providerDistance)

    private fun departure(line: String, minutes: Long, trip: String? = null) = Departure(
        line = line, product = "bus", direction = "Somewhere",
        time = at.plusMinutes(minutes), plannedTime = at.plusMinutes(minutes),
        delaySeconds = 0, platform = null, cancelled = false, tripId = trip,
    )

    @Test
    fun stopsAreOrderedByTheDistanceTheyShow() {
        // The provider's order (by its own walk from elsewhere) is not the one shown.
        val stops = listOf(stop("far", 840.0, 300), stop("near", 390.0, 500), stop("middle", 690.0, 100))
        val result = stopsByDistance(stops, here)
        assertEquals(listOf("near", "middle", "far"), result.map { it.id })
        assertEquals(listOf(390, 690, 840), result.map { it.distanceMeters })
        // Every shown distance is no shorter than the one before.
        assertTrue(result.zipWithNext().all { (a, b) -> (a.distanceMeters ?: 0) <= (b.distanceMeters ?: 0) })
    }

    @Test
    fun theSelectedStopStaysOnTopAndStopsWithoutDistanceFollow() {
        val picked = Stop("picked", "Picked by name", 52.6, 13.4, null)
        val stops = listOf(picked, stop("far", 950.0), stop("near", 390.0), stop("selected", 690.0))
        val result = stopsByDistance(stops, here, firstId = "selected")
        assertEquals(listOf("selected", "near", "far", "picked"), result.map { it.id })
        assertEquals(null, result.last().distanceMeters)
    }

    @Test
    fun withoutAPositionTheGivenDistancesCount() {
        val stops = listOf(stop("b", 0.0, 700), stop("a", 0.0, 300))
        assertEquals(listOf("a", "b"), stopsByDistance(stops, null).map { it.id })
    }

    @Test
    fun boardsOfSeveralStopsMergeByTimeWithTheirStop() {
        val first = stop("first", 100.0)
        val second = stop("second", 300.0)
        val merged = mergeStopBoards(
            listOf(
                StopDepartures(first, listOf(departure("M48", 3), departure("M85", 9))),
                StopDepartures(second, listOf(departure("104", 1), departure("106", 5))),
            ),
        )
        assertEquals(listOf("104", "M48", "106", "M85"), merged.map { it.line })
        assertEquals(listOf("second", "first", "second", "first"), merged.map { it.stop?.id })
        assertTrue(merged.all { it.stop?.distanceMeters == null })
    }

    @Test
    fun aRunAtTwoStopsIsListedOnceAtTheFirst() {
        val first = stop("first", 100.0)
        val second = stop("second", 300.0)
        val merged = mergeStopBoards(
            listOf(
                StopDepartures(first, listOf(departure("M48", 4, trip = "t1"))),
                StopDepartures(second, listOf(departure("M48", 2, trip = "t1"), departure("106", 3, trip = "t2"))),
            ),
        )
        assertEquals(listOf("106", "M48"), merged.map { it.line })
        assertEquals("first", merged.last().stop?.id)
    }

    @Test
    fun theBoardEndsWhereAFullBoardEnds() {
        val first = stop("first", 100.0)
        val second = stop("second", 300.0)
        val merged = mergeStopBoards(
            listOf(
                // As many as asked for: later ones of this stop are missing, not absent.
                StopDepartures(first, listOf(departure("M48", 2), departure("M48", 6)), full = true),
                StopDepartures(second, listOf(departure("106", 4), departure("106", 12)), full = false),
            ),
        )
        assertEquals(listOf(2L, 4L, 6L), merged.map { java.time.Duration.between(at, it.time!!).toMinutes() })
    }

    @Test
    fun leaveTimesGoByTheWalkToEachStop() {
        val near = stop("near", 100.0)
        val far = stop("far", 900.0)
        val merged = mergeStopBoards(
            listOf(
                StopDepartures(near, listOf(departure("M48", 10))),
                StopDepartures(far, listOf(departure("106", 12))),
            ),
        )
        val leaves = leaveTimes(
            merged, here, null,
            walk = { _, to -> if (to == far.position) 600 else 60 },
            place = { departure, _ -> departure.stop?.position },
        )
        // The 106 leaves later but from the farther stop: the user must set off for it first.
        assertEquals(listOf("106", "M48"), leaves.map { it.departure.line })
        assertEquals(at.plusMinutes(2), leaves[0].leaveAt)
        assertEquals(at.plusMinutes(9), leaves[1].leaveAt)
        assertEquals(listOf("106", "M48"), catchableLeaves(leaves, at).map { it.departure.line })
        // Missed a minute ago: still listed (greyed), with a time to leave in the past.
        val missed = at.plusMinutes(3)
        assertEquals(listOf("106", "M48"), catchableLeaves(leaves, missed).map { it.departure.line })
        assertTrue((secondsToLeave(leaves[0], missed) ?: 0L) < 0)
        // Missed longer ago: no longer reachable, and gone from the list.
        assertEquals(listOf("M48"), catchableLeaves(leaves, at.plusMinutes(5)).map { it.departure.line })
    }

    @Test
    fun anyStopCloseEnoughMakesTheBoardWalkable() {
        val near = stop("near", 500.0)
        val tooFar = stop("tooFar", 5_000.0)
        assertTrue(anyWalkableFrom(here, listOf(tooFar, near)))
        assertFalse(anyWalkableFrom(here, listOf(tooFar)))
    }

    @Test
    fun noticesOfTheStationFirstAndOnlyThoseOfTheFilteredLines() {
        val lift = Warning("Aufzug defekt", "Der Aufzug zum Bahnsteig ist außer Betrieb.")
        val works = Warning("Bauarbeiten", "Umleitung zwischen A und B.")
        val other = Warning("Umleitung", "Die Haltestelle C wird nicht bedient.")
        val departures = listOf(
            departure("M48", 2).copy(warnings = listOf(works)),
            departure("M48", 4).copy(warnings = listOf(works)),
            departure("106", 3).copy(warnings = listOf(other, lift)),
        )
        val all = boardDisruptions(departures)
        assertEquals(3, all.size)
        assertEquals("Aufzug defekt", all.first().summary)
        val ofM48 = boardDisruptions(departures, setOf("M48"))
        assertEquals(listOf("Aufzug defekt", "Bauarbeiten"), ofM48.map { it.summary })
    }
}
