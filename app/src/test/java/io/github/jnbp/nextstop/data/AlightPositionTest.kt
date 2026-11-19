package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Reminders to get off timed by the position of the device, on a made-up ride. */
class AlightPositionTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-01-01T${clock}:00+01:00")

    private val ride = Leg(
        "S1", "suburban", false, "Example A", "Example C", at("10:00"), at("10:00"), at("10:20"), at("10:20"), null, null, null, false,
        stopovers = listOf(
            Stopover("Example A", null, at("10:00")),
            Stopover("Example B", at("10:15"), at("10:15")),
            Stopover("Example C", at("10:20"), null),
        ),
        tripId = "s1",
    )
    private val journey = Journey(listOf(ride))
    private val events = alightEvents(journey)

    private fun fix(kind: FixKind, toEnd: Double, nextStop: Int) =
        PositionFix(journey.id, 0, kind, 0.0, toEnd, nextStop, 0L)

    @Test
    fun nextStopToGetOffComesEarlyByPosition() {
        val now = at("10:08")
        val moved = alightEventsByPosition(events, journey, fix(FixKind.OnRide, 2000.0, 2), now)
        val due = dueAlight(moved, emptySet(), now)
        assertEquals(true, due?.early)
    }

    @Test
    fun aLateTrainHoldsTheReminderBack() {
        // 10:19 by the times; the train is still before Example B, far from the end.
        val now = at("10:19")
        val moved = alightEventsByPosition(events, journey, fix(FixKind.OnRide, 4000.0, 1), now)
        assertNull(dueAlight(moved, emptySet(), now))
    }

    @Test
    fun closeToTheEndArrivingIsDue() {
        val now = at("10:10")
        val moved = alightEventsByPosition(events, journey, fix(FixKind.OnRide, 400.0, 2), now)
        assertEquals(false, dueAlight(moved, emptySet(), now)?.early)
    }

    @Test
    fun withoutAFixTheTimesStand() {
        assertEquals(events, alightEventsByPosition(events, journey, null, at("10:10")))
    }
}
