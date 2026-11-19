package io.github.jnbp.nextstop.wear.ui

import io.github.jnbp.nextstop.wear.data.ChangeRating
import io.github.jnbp.nextstop.wear.data.PlanRide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverviewTimelineTest {
    private val minute = 60_000L
    private val t0 = 1_800_000_000_000L
    private val first = PlanRide("U9", "subway", null, "Example Square", "Sample Street", t0 + 5 * minute, t0 + 15 * minute, walkBefore = 5)
    private val second = PlanRide(
        "S7", "suburban", null, "Sample Street", "Test Park", t0 + 21 * minute, t0 + 40 * minute,
        walkBefore = 2, rating = ChangeRating.Comfortable, spare = 4,
    )

    @Test
    fun theRowsFollowTheListOfTheOverview() {
        val rows = overviewTimeline(listOf(first, second), planWalkEnd = 3, arrival = t0 + 43 * minute, destination = "Mock Hall")
        assertEquals(
            listOf(TimelineKind.Connector, TimelineKind.Ride, TimelineKind.Connector, TimelineKind.Ride, TimelineKind.EndWalk, TimelineKind.Arrival),
            rows.map { it.kind },
        )
        assertEquals(listOf(0, 0, 1, 1, 2, 2), rows.map { it.ride })
        // The walk to the first stop starts its minutes before the departure, at the first stop.
        assertEquals(t0, rows[0].time)
        assertEquals("Example Square", rows[0].place)
        // On the first ride the next change has 4 minutes to spare; the change row itself too.
        assertEquals(t0 + 5 * minute, rows[1].time)
        assertEquals("U9", rows[1].line)
        assertEquals(4, rows[1].buffer)
        assertEquals(t0 + 15 * minute, rows[2].time)
        assertEquals("Sample Street", rows[2].place)
        assertEquals(4, rows[2].buffer)
        // After the last change: no buffer.
        assertNull(rows[3].buffer)
        assertEquals(t0 + 40 * minute, rows[4].time)
        assertEquals("Test Park", rows[4].place)
        assertEquals("Mock Hall", rows[5].place)
        assertEquals(t0 + 43 * minute, rows[5].time)
    }

    @Test
    fun aRideWithoutWalkOrRatingHasNoConnector() {
        val direct = first.copy(walkBefore = 0)
        val rows = overviewTimeline(listOf(direct), planWalkEnd = 0, arrival = direct.arrival, destination = "Test Park")
        assertEquals(listOf(TimelineKind.Ride, TimelineKind.Arrival), rows.map { it.kind })
    }

    @Test
    fun theHeaderAndTheActionsHaveNoTime() {
        val rows = overviewTimeline(listOf(first), planWalkEnd = 0, arrival = first.arrival, destination = "Sample Street")
        assertNull(timelineAt(rows, 0))
        assertEquals(TimelineKind.Connector, timelineAt(rows, 1)?.kind)
        assertEquals(TimelineKind.Arrival, timelineAt(rows, rows.size)?.kind)
        assertNull(timelineAt(rows, rows.size + 1))
    }

    @Test
    fun theTimeTravelPillOnlyWithTheOption() {
        // Off by default: the crown just scrolls the overview.
        assertEquals(false, timeTravelShown(option = false, hasPlan = true, ambient = false))
        assertEquals(true, timeTravelShown(option = true, hasPlan = true, ambient = false))
        assertEquals(false, timeTravelShown(option = true, hasPlan = false, ambient = false))
        assertEquals(false, timeTravelShown(option = true, hasPlan = true, ambient = true))
    }
}
