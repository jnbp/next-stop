package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #34: two trams 66 bunched by their delays. The first left Bonn-Gronau at 20:14 (+26) and was
 * at Bonn Hbf at 20:21 (+24); the second left Bonn Hbf at 20:27 (+10) and reached Siegburg at
 * 20:47 (+6), while the first one was there at 20:45. The search built "66 to Bonn Hbf, same
 * platform, change into the 66 here, 66 to Siegburg", one change too many and two minutes
 * later, as a column of its own beside the journey that simply stays on the first 66.
 */
class SameLineChangeTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-11-17T$clock:00+01:00")
    private val gronau = Stop("de:05314:61101", "Bonn-Gronau", 50.7130, 7.1300, null)
    private val bonnHbf = Stop("de:05314:61908", "Bonn Hbf", 50.7320, 7.0970, null)
    private val siegburg = Stop("de:05210:9022", "Siegburg Bf", 50.7930, 7.2030, null)
    private val deutz = Stop("de:05315:11209", "Köln Messe/Deutz", 50.9410, 6.9750, null)

    private fun ride(
        line: String,
        product: String,
        from: Stop,
        to: Stop,
        leaves: String,
        planLeaves: String,
        arrives: String,
        planArrives: String,
        trip: String,
        direction: String?,
        fromPlatform: String? = null,
        toPlatform: String? = null,
    ) = Leg(
        line, product, false, from.name, to.name, at(leaves), at(planLeaves), at(arrives), at(planArrives),
        fromPlatform, toPlatform, direction, false, realtime = true,
        fromPlace = from, toPlace = to, tripId = trip,
    )

    private fun walk(from: Stop, to: Stop, leaves: String, arrives: String) = Leg(
        null, "", true, from.name, to.name, at(leaves), at(leaves), at(arrives), at(arrives),
        null, null, null, false, fromPlace = from, toPlace = to,
    )

    // The first 66, the traveller's, ahead by its delay of 26 minutes.
    private val first66ToHbf = ride("66", "tram", gronau, bonnHbf, "20:14", "19:48", "20:21", "19:57", "66-a", "Siegburg Bf", "13", "13")
    // The second 66, from Bonn Hbf to Siegburg, 10 minutes late.
    private val second66 = ride("66", "tram", bonnHbf, siegburg, "20:27", "20:17", "20:47", "20:41", "66-b", "Siegburg Bf", "13", "18")
    // The first 66 all the way, as the other column found it.
    private val first66ToSiegburg = ride("66", "tram", gronau, siegburg, "20:14", "19:48", "20:45", "20:20", "66-a", "Siegburg Bf", "13", "18")
    private val ice = ride("ICE 41", "express", siegburg, deutz, "21:00", "21:00", "21:15", "21:15", "ice41", "Köln Hbf", "2", "11")

    private val changing = Journey(listOf(first66ToHbf, second66, ice))
    private val staying = Journey(listOf(first66ToSiegburg, ice))

    @Test
    fun aChangeIntoTheSameLineBehindIsStayingSeated() {
        assertTrue(staysSeatedAhead(first66ToHbf, second66))
        val merged = mergeThroughRuns(changing)
        assertEquals(1, merged.transfers)
        val tram = merged.rides.first()
        assertEquals("66", tram.line)
        assertEquals(gronau.name, tram.from)
        assertEquals(siegburg.name, tram.to)
        // The vehicle stays the first one; its time at Siegburg is that of the one behind at the latest.
        assertEquals("66-a", tram.tripId)
        assertEquals(at("20:14"), tram.departure)
        assertEquals(at("20:47"), tram.arrival)
        assertTrue(tram.arrivalEstimated)
        assertEquals(listOf(first66ToHbf, second66), tram.seatedParts)
        assertTrue(tram.throughRuns.isEmpty())
    }

    @Test
    fun aWalkOrAnotherPlatformKeepsTheChange() {
        // A walk the provider sends between the two rides: the traveller gets off there.
        val withWalk = Journey(listOf(first66ToHbf, walk(bonnHbf, bonnHbf, "20:21", "20:22"), second66, ice))
        assertEquals(2, mergeThroughRuns(withWalk).transfers)
        // Another platform for the same line and direction may be another branch.
        assertFalse(staysSeatedAhead(first66ToHbf, second66.copy(departurePlatform = "14")))
    }

    @Test
    fun theOtherColumnTellsTheTimeOfTheOwnVehicleAndBothAreOne() {
        val merged = mergeThroughRuns(changing)
        val timed = withSeatedTimes(listOf(merged, staying))
        val tram = timed.first().rides.first()
        assertEquals(at("20:45"), tram.arrival)
        assertEquals(at("20:20"), tram.plannedArrival)
        assertFalse(tram.arrivalEstimated)
        assertEquals(journeyKey(staying), journeyKey(timed.first()))
        // In the chart and the list: one column, not two.
        val shown = mergeSearchPages(emptyList(), listOf(merged, staying))
        assertEquals(1, shown.size)
        assertEquals(1, shown.single().transfers)
        assertEquals(at("21:15"), shown.single().arrival)
    }

    @Test
    fun aLaterVehicleThatProvablyGetsThereFirstKeepsTheChange() {
        // The other column shows the first 66 at Siegburg only at 20:50: the change was worth it.
        val slowFirst = first66ToSiegburg.copy(arrival = at("20:50"))
        val merged = mergeThroughRuns(changing)
        val timed = withSeatedTimes(listOf(merged, Journey(listOf(slowFirst, ice))))
        assertEquals(2, timed.first().transfers)
        assertEquals(listOf(first66ToHbf, second66, ice), timed.first().legs)
    }

    @Test
    fun aShortRunThatEndsAtTheChangeIsARealChange() {
        val shortRun = first66ToHbf.copy(direction = "Bonn Hbf")
        assertFalse(staysSeatedAhead(shortRun, second66))
        assertEquals(2, mergeThroughRuns(Journey(listOf(shortRun, second66, ice))).transfers)
    }

    @Test
    fun anotherDirectionOrLineOrAVehicleAheadIsARealChange() {
        assertFalse(staysSeatedAhead(first66ToHbf, second66.copy(direction = "Bad Honnef")))
        assertFalse(staysSeatedAhead(first66ToHbf, second66.copy(line = "16")))
        // The other 66 leaves before the traveller's is there: it runs ahead, the change may gain.
        assertFalse(staysSeatedAhead(first66ToHbf, second66.copy(departure = at("20:19"))))
        // Without a direction on either ride nothing is assumed.
        assertFalse(staysSeatedAhead(first66ToHbf.copy(direction = null), second66))
        // A cancelled ride is never joined.
        assertFalse(staysSeatedAhead(first66ToHbf, second66.copy(cancelled = true)))
    }

    @Test
    fun aStayAtAPlaceOnTheWayIsNeverSkipped() {
        val via = JourneyVia(bonnHbf, minutes = 15, afterLegs = 1)
        val merged = mergeThroughRuns(changing.copy(via = via))
        assertEquals(2, merged.transfers)
        // Passing through (no stay) is no reason to get off.
        val passing = mergeThroughRuns(changing.copy(via = via.copy(minutes = 0)))
        assertEquals(1, passing.transfers)
        assertNotNull(passing.via)
        assertEquals(1, passing.via!!.afterLegs)
    }

    @Test
    fun aDirectionNamedALittleDifferentlyIsTheSame() {
        assertTrue(staysSeatedAhead(first66ToHbf.copy(direction = "Siegburg"), second66))
    }
}
