package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The time of a change as every screen shows it: from the live arrival to the live departure,
 * rated by what is left beyond what the change needs. The case of a real trip: bus 248 into
 * S Südkreuz Bhf/Ostseite at 17:50, on with the RE3 (made-up ids).
 */
class ChangeTimeTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-11-12T$clock:00+01:00")
    private val ostseite = Stop("900058100::bus", "S Südkreuz Bhf/Ostseite", 52.4755, 13.3660, null)
    private val suedkreuz = Stop("900058101", "S Südkreuz Bhf", 52.4752, 13.3655, null, products = setOf("suburban", "regional", "express"))
    private val start = Stop("900068201", "Start", 52.46, 13.33, null)
    private val end = Stop("900100003", "Ziel", 52.52, 13.41, null)

    private fun bus(arrives: String, plannedArrives: String = arrives) = Leg(
        "248", "bus", false, start.name, ostseite.name, at("17:30"), at("17:30"), at(arrives), at(plannedArrives),
        null, null, null, false, realtime = true, fromPlace = start, toPlace = ostseite, tripId = "bus248",
    )

    private fun re3(leaves: String, plannedLeaves: String, cancelled: Boolean = false) = Leg(
        "RE3", "regional", false, suedkreuz.name, end.name, at(leaves), at(plannedLeaves), at("18:10"), at("18:10"),
        "5", null, null, cancelled, realtime = true, fromPlace = suedkreuz, toPlace = end, tripId = "re3",
    )

    @Test
    fun delayedConnectionCountsWithItsDelay() {
        // Planned 17:52, 6 minutes late: it leaves 17:58, 8 minutes after the bus is in.
        val time = changeTime(bus("17:50"), re3("17:58", "17:52"), walkMinutes = 3)
        assertEquals(8L, time.minutes)
        // A walk of 3 minutes from the bus stop needs 4: 4 to spare.
        assertEquals(4L, time.need)
        assertEquals(4L, time.spare)
        assertEquals(Tightness.Tight, time.tightness)
        assertTrue(time.possible)
    }

    @Test
    fun aRidersTripAtSuedkreuz() {
        // Bus in at 17:50; the RE3 shown "17:52 +6": live 17:52, planned 17:46. Every screen
        // shows 2 minutes from arrival to departure; with a 3-minute walk that is too late,
        // and the rating says so with the live times, not the timetable.
        val time = changeTime(bus("17:50", plannedArrives = "17:47"), re3("17:52", "17:46"), walkMinutes = 3)
        assertEquals(2L, time.minutes)
        assertEquals(4L, time.need)
        assertEquals(-2L, time.spare)
        assertEquals(Tightness.VeryRisky, time.tightness)
        assertFalse(time.possible)
        // Without the walk (same station) it is a sprint that can still be made.
        val near = changeTime(bus("17:50"), re3("17:52", "17:46"))
        assertEquals(2L, near.minutes)
        assertTrue(near.possible)
    }

    @Test
    fun journeyChangeOfARidersTrip() {
        val walk = Leg(
            null, "walking", true, ostseite.name, suedkreuz.name, at("17:50"), at("17:50"), at("17:53"), at("17:53"),
            null, null, null, false, fromPlace = ostseite, toPlace = suedkreuz,
        )
        val change = journeyChangesOf(Journey(listOf(bus("17:50"), walk, re3("17:52", "17:46")))).single()
        assertEquals(2L, change.time.minutes)
        assertEquals(change.spare, change.time.spare)
        assertEquals(change.tightness, change.time.tightness)
        // Too late exactly where the 2 minutes do not cover the walk the journey counts.
        assertEquals(2L >= change.walkMinutes, change.time.possible)
    }

    @Test
    fun goneBeforeArrival() {
        val time = changeTime(bus("17:55"), re3("17:52", "17:52"), walkMinutes = 3)
        assertEquals(-3L, time.minutes)
        assertFalse(time.possible)
        assertEquals(Tightness.VeryRisky, time.tightness)
    }

    @Test
    fun journeyChangeIsTheSameNumber() {
        // The change of the journey, the change row and the watch read one ChangeTime.
        val walk = Leg(
            null, "walking", true, ostseite.name, suedkreuz.name, at("17:50"), at("17:50"), at("17:53"), at("17:53"),
            null, null, null, false, fromPlace = ostseite, toPlace = suedkreuz,
        )
        val journey = Journey(listOf(bus("17:50"), walk, re3("17:58", "17:52")))
        val change = journeyChangesOf(journey).single()
        assertEquals(8L, change.time.minutes)
        assertEquals(change.tightness, change.time.tightness)
        assertEquals(change.spare, change.time.spare)
        assertEquals(changeTime(change.arriving, change.next, change.walkMinutes).minutes, change.time.minutes)
    }

    @Test
    fun partialMinutesRoundDown() {
        val time = changeTime(bus("17:50").copy(arrival = at("17:50").plusSeconds(30)), re3("17:52", "17:52"))
        assertEquals(1L, time.minutes)
        val late = changeTime(bus("17:52").copy(arrival = at("17:52").plusSeconds(30)), re3("17:52", "17:52"))
        assertEquals(-1L, late.minutes)
    }
}
