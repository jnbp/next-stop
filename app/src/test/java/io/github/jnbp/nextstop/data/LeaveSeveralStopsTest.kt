package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "When to leave" over several stops at once: every stop keeps its rows, each with its own walk,
 * also where a near stop with many departures sits next to farther ones.
 */
class LeaveSeveralStopsTest {
    private val here = 52.5000 to 13.3000
    private val now = OffsetDateTime.parse("2026-11-14T10:00:00+01:00")

    // A stop [north] metres due north of [here].
    private fun stop(id: String, north: Double) = Stop(id, "Stop $id", here.first + north / 111_320.0, here.second, null)

    private fun departure(line: String, minutes: Long, at: OffsetDateTime = now) = Departure(
        line = line, product = "subway", direction = "Somewhere",
        time = at.plusMinutes(minutes), plannedTime = at.plusMinutes(minutes),
        delaySeconds = 0, platform = null, cancelled = false, tripId = "$line-$minutes",
    )

    private val near = stop("A", 50.0)
    private val middle = stop("B", 700.0)
    private val far = stop("C", 750.0)

    // The near stop: a departure every minute, as many as asked for (full). The farther ones:
    // every six minutes from 3 minutes on.
    private val boards = listOf(
        StopDepartures(near, (0L..11L).map { departure("U1", it) }, full = true),
        StopDepartures(middle, listOf(3L, 9L, 15L, 21L, 27L, 33L).map { departure("U2", it) }, full = true),
        StopDepartures(far, listOf(4L, 10L, 16L, 22L, 28L, 34L).map { departure("U3", it) }, full = true),
    )

    private fun walk(stop: Stop): Int? = stop.position?.let { leaveWalkSeconds(here, it, knownMeters = null, factor = 1.0) }

    private fun leaves(board: List<Departure>) = catchableLeaves(
        leaveTimes(board, here, null, walk = { from, to -> leaveWalkSeconds(from, to, knownMeters = null, factor = 1.0) }),
        now,
    )

    @Test
    fun everyStopKeepsItsRowsWithItsOwnWalk() {
        walkSpeedFactor = 1.0
        val rows = leaves(mergeStopBoards(boards, walkSeconds = ::walk))
        assertEquals(setOf("A", "B", "C"), rows.mapNotNull { it.departure.stop?.id }.toSet())
        rows.forEach { row ->
            val stop = boards.first { it.stop.id == row.departure.stop?.id }.stop
            assertEquals(walk(stop), row.walkSeconds)
        }
        // The rows go by the time to set off.
        val times = rows.mapNotNull { it.leaveAt?.toInstant() }
        assertEquals(times.sorted(), times)
    }

    @Test
    fun cutByTheTimeOfDepartureOnlyTheNearStopWasLeft() {
        // The bug: the board ended where the near stop's ends by departure (11 minutes), so the
        // farther stops kept only departures they cannot be reached for on foot.
        walkSpeedFactor = 1.0
        val rows = leaves(mergeStopBoards(boards))
        assertEquals(setOf("A"), rows.mapNotNull { it.departure.stop?.id }.toSet())
        // By departure, without a walk, every stop is still listed.
        assertEquals(setOf("A", "B", "C"), mergeStopBoards(boards).mapNotNull { it.stop?.id }.toSet())
    }

    @Test
    fun theDeparturesOfAFartherStopAreAskedFromWhenTheUserCanBeThere() {
        // A walk of 10 minutes: from 8 minutes on (2 a missed one stays listed).
        assertEquals(now.plusMinutes(8), leaveDeparturesFrom(600, null, now))
        // A short walk changes nothing: from now (null) or the picked time.
        assertEquals(null, leaveDeparturesFrom(90, null, now))
        assertEquals(null, leaveDeparturesFrom(null, null, now))
        assertEquals(now.plusMinutes(30), leaveDeparturesFrom(90, now.plusMinutes(30), now))
        // A picked time later than the walk counts; an earlier one gives way to the walk.
        assertEquals(now.plusMinutes(30), leaveDeparturesFrom(600, now.plusMinutes(30), now))
        assertEquals(now.plusMinutes(8), leaveDeparturesFrom(600, now.plusMinutes(1), now))
        // Asked from then, all the far stop's rows can still be caught.
        val from = leaveDeparturesFrom(walk(far), null, now)!!
        val later = (0L..5L).map { departure("U3", it * 6, at = from).copy(stop = far) }
        assertEquals(later.size, leaves(later).size)
    }
}
