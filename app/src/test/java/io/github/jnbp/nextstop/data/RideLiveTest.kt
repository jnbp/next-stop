package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #274: the RJ 171 Berlin Suedkreuz - Dresden-Neustadt on 19.11.2026 came from Transitous
 * without realtime, while DB knew it left at 07:59 instead of 07:37 (+22) and reached
 * Dresden-Neustadt at 09:17 instead of 09:00 (+17). The live times of HAFAS for the same run
 * stand in; live times of Transitous itself stay.
 */
class RideLiveTest {
    private fun at(time: String): OffsetDateTime = OffsetDateTime.parse("2026-11-19T$time:00+01:00")
    private val suedkreuz = Stop("tr:suedkreuz", "Berlin Südkreuz", 52.4753, 13.3655, null)
    private val elsterwerda = Stop("tr:elsterwerda", "Elsterwerda", 51.4597, 13.5196, null)
    private val dresden = Stop("tr:neustadt", "Dresden-Neustadt", 51.0654, 13.7408, null)

    private fun ride(realtime: Boolean = false) = Leg(
        line = "RJ 171", product = "express", walking = false, from = suedkreuz.name, to = dresden.name,
        departure = at("07:37"), plannedDeparture = at("07:37"), arrival = at("09:00"), plannedArrival = at("09:00"),
        departurePlatform = "3", arrivalPlatform = null, direction = "Praha hl.n.", cancelled = false, realtime = realtime,
        fromPlace = suedkreuz, toPlace = dresden, tripId = "tr:171",
        stopovers = listOf(Stopover("Elsterwerda", at("08:19"), at("08:20"), elsterwerda, plannedArrival = at("08:19"), plannedDeparture = at("08:20"))),
    )

    // The board of HAFAS at the station, a little off its position as HAFAS has it.
    private fun boardRun(delayMinutes: Long?, platform: String? = "4", cancelled: Boolean = false) = Departure(
        line = "RJ 171", product = "express", direction = "Praha hl.n.",
        time = delayMinutes?.let { at("07:37").plusMinutes(it) } ?: at("07:37"), plannedTime = at("07:37"),
        delaySeconds = delayMinutes?.let { (it * 60).toInt() }, platform = platform, cancelled = cancelled,
        tripId = "1|171|0|80|19112026", at = 52.4756 to 13.3650,
    )

    private fun course() = listOf(
        Stopover("Berlin Südkreuz", null, at("07:59"), Stop("900058101", "S Südkreuz Bhf (Berlin)", 52.4756, 13.3650, null), delaySeconds = 1320, plannedDeparture = at("07:37")),
        Stopover("Elsterwerda", at("08:38"), at("08:39"), Stop("x", "Elsterwerda", 51.4600, 13.5200, null), delaySeconds = 1140, plannedArrival = at("08:19"), plannedDeparture = at("08:20")),
        Stopover("Dresden-Neustadt", at("09:17"), at("09:19"), Stop("y", "Dresden-Neustadt", 51.0656, 13.7405, null), delaySeconds = 1020, plannedArrival = at("09:00"), plannedDeparture = at("09:02")),
    )

    @Test
    fun theWholeCourseGivesTheLateTimes() {
        val live = RunLive(liveStopsOf(course()), wholeRun = true, atMillis = 0)
        val leg = ride().withRunLive(live)
        assertEquals(at("07:59"), leg.departure)
        assertEquals(at("09:17"), leg.arrival)
        assertEquals(at("07:37"), leg.plannedDeparture)
        assertEquals(at("09:00"), leg.plannedArrival)
        assertEquals(22L, leg.departureDelayMinutes)
        assertEquals(17L, leg.arrivalDelayMinutes)
        assertTrue(leg.realtime)
        assertTrue(leg.hafasLive)
        assertEquals(at("08:38"), leg.stopovers[0].arrival)
        assertEquals(at("08:20"), leg.stopovers[0].plannedDeparture)
        // Applied again with newer data: still taken (the leg's own realtime is that of HAFAS).
        val later = RunLive(liveStopsOf(course().map { it.copy(arrival = it.arrival?.plusMinutes(2)) }), true, 1)
        assertEquals(at("09:19"), leg.withRunLive(later).arrival)
    }

    @Test
    fun aBoardAloneCarriesItsDelayOn() {
        val live = RunLive(listOfNotNull(liveStopOf(boardRun(22), suedkreuz.position)), wholeRun = false, atMillis = 0)
        val leg = ride().withRunLive(live)
        assertEquals(at("07:59"), leg.departure)
        assertEquals(at("09:22"), leg.arrival)
        assertEquals(at("08:42"), leg.stopovers[0].departure)
        assertEquals("4", leg.departurePlatform)
    }

    @Test
    fun noLiveDataNoChange() {
        assertNull(liveStopOf(boardRun(null), suedkreuz.position))
        assertTrue(liveStopsOf(course().map { it.copy(arrival = it.plannedArrival, departure = it.plannedDeparture, delaySeconds = null) }).isEmpty())
        val leg = ride()
        assertSame(leg, leg.withRunLive(RunLive(emptyList(), true, 0)))
    }

    @Test
    fun theOwnRealtimeOfTransitousStays() {
        val leg = ride(realtime = true)
        assertSame(leg, leg.withRunLive(RunLive(liveStopsOf(course()), true, 0)))
    }

    @Test
    fun anotherStopOrTimeIsNotTaken() {
        // The same minute at another station, or the same station another hour: not this ride.
        val elsewhere = RunLive(listOf(LiveStop(dresden.position, "", null, null, at("07:37"), at("07:59"))), false, 0)
        assertEquals(at("07:37"), ride().withRunLive(elsewhere).departure)
        val otherHour = RunLive(listOf(LiveStop(suedkreuz.position, "", null, null, at("08:37"), at("08:59"))), false, 0)
        assertEquals(at("07:37"), ride().withRunLive(otherHour).departure)
    }

    @Test
    fun aCancelledRunAtTheBoardCancelsTheRide() {
        val live = RunLive(listOfNotNull(liveStopOf(boardRun(null, cancelled = true), suedkreuz.position)), false, 0)
        assertTrue(ride().withRunLive(live).cancelled)
    }

    @Test
    fun aBoardOfTransitousTakesTheLiveTime() {
        val departure = boardRun(null, platform = "3").copy(tripId = "tr:171", at = suedkreuz.position)
        val live = RunLive(listOfNotNull(liveStopOf(boardRun(22, platform = null), suedkreuz.position)), false, 0)
        val shown = departure.withRunLive(live)
        assertEquals(at("07:59"), shown.time)
        assertEquals(22 * 60, shown.delaySeconds)
        assertEquals("3", shown.platform)
        // A departure with live data of its own stays.
        val own = departure.copy(delaySeconds = 60)
        assertSame(own, own.withRunLive(live))
    }

    @Test
    fun aCourseKeepsTheBoardsPlatform() {
        val board = RunLive(listOfNotNull(liveStopOf(boardRun(20), suedkreuz.position)), false, 0)
        val merged = mergedLive(board, RunLive(liveStopsOf(course()), true, 5))
        assertTrue(merged.wholeRun)
        assertEquals(5L, merged.atMillis)
        assertEquals(3, merged.stops.size)
        assertEquals("4", merged.stops[0].platform)
        assertEquals(at("07:59"), merged.stops[0].departure)
        assertFalse(merged.stops[0].cancelled)
    }

    @Test
    fun theStoreKeepsLiveTimesForAWhile() {
        RideKindFixes.clear()
        assertFalse(RideKindFixes.liveDue("tr:171", 0))
        RideKindFixes.link("tr:171", "1|171|0|80|19112026", 0)
        assertEquals("1|171|0|80|19112026", RideKindFixes.linkOf("tr:171", 1000))
        assertTrue(RideKindFixes.liveDue("tr:171", 0))
        val before = RideKindFixes.version.value
        RideKindFixes.putLive("tr:171", RunLive(liveStopsOf(course()), true, 0))
        assertTrue(RideKindFixes.version.value > before)
        assertFalse(RideKindFixes.liveDue("tr:171", 10_000))
        assertTrue(RideKindFixes.liveDue("tr:171", 60_000))
        assertEquals(3, RideKindFixes.live("tr:171", 60_000)?.stops?.size)
        assertNull(RideKindFixes.live("tr:171", 11L * 60 * 1000))
        // Through the store: a journey of this ride gets the late times.
        val journey = withKnownRuns(Journey(legs = listOf(ride())), 1000)
        assertEquals(at("09:17"), journey.legs[0].arrival)
        RideKindFixes.clear()
    }
}
