package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** #184 on the watch: the phase of a ride waited for, by the phone's rule (waitPhase there). */
class WaitPhaseTest {
    private val now = 1_000_000_000L
    private val minute = 60_000L

    private fun waiting(time: Long, dueAt: Long = 0, missed: Boolean = false) = TripSnapshot(
        active = true, kind = StepKind.Board, line = "S1", time = time, waiting = true, dueAt = dueAt, missed = missed,
    )

    @Test
    fun aMinuteOrMoreAwayCountsDown() {
        assertEquals(WatchWaitPhase.Coming to 0L, waitPhaseOf(waiting(now + minute), now))
        // Not waited for at the stop: the countdown as always, also when late.
        assertEquals(WatchWaitPhase.Coming to 0L, waitPhaseOf(waiting(now - 5 * minute).copy(waiting = false), now))
    }

    @Test
    fun dueOrJustLateIsAnyMoment() {
        assertEquals(WatchWaitPhase.AnyMoment to 0L, waitPhaseOf(waiting(now + 59_000), now))
        assertEquals(WatchWaitPhase.AnyMoment to 0L, waitPhaseOf(waiting(now - 119_000), now))
    }

    @Test
    fun fromTwoMinutesLateItIsOverdue() {
        assertEquals(WatchWaitPhase.Overdue to 2L, waitPhaseOf(waiting(now - 2 * minute), now))
        assertEquals(WatchWaitPhase.Overdue to 4L, waitPhaseOf(waiting(now - 4 * minute - 30_000), now))
    }

    @Test
    fun thePhonesDueTimeWins() {
        // The timetable says now, the vehicle is due in 5 minutes by its position.
        assertEquals(WatchWaitPhase.Coming to 0L, waitPhaseOf(waiting(now, dueAt = now + 5 * minute), now))
    }

    @Test
    fun aMissedRideIsNoWait() {
        assertEquals(WatchWaitPhase.Coming to 0L, waitPhaseOf(waiting(now - 3 * minute, missed = true), now))
    }

    @Test
    fun theDueTimeTravels() {
        val trip = waiting(now, dueAt = now + 3 * minute)
        assertEquals(now + 3 * minute, TripSnapshot.parse(trip.toJson()).dueAt)
    }
}
