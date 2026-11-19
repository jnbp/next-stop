package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.Stop
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #34 at the choice of connections ("Welchen Anschluss willst du nehmen?"): sitting in a 66
 * that runs ahead of the next one, that next 66 is no connection to change to. And the tile of
 * the chosen connection says the arrival of the journey above it (the head said 21:15, the
 * tile of the ICE 41 "an 21:18").
 */
class SameLineOptionsTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-11-17T$clock:00+01:00")
    private val gronau = Stop("de:05314:61101", "Bonn-Gronau", 50.7130, 7.1300, null)
    private val bonnHbf = Stop("de:05314:61908", "Bonn Hbf", 50.7320, 7.0970, null)
    private val siegburg = Stop("de:05210:9022", "Siegburg Bf", 50.7930, 7.2030, null)
    private val deutz = Stop("de:05315:11209", "Köln Messe/Deutz", 50.9410, 6.9750, null)

    private fun ride(line: String, product: String, from: Stop, to: Stop, leaves: String, arrives: String, trip: String, direction: String) = Leg(
        line, product, false, from.name, to.name, at(leaves), at(leaves), at(arrives), at(arrives),
        null, null, direction, false, realtime = true, fromPlace = from, toPlace = to, tripId = trip,
    )

    private val tram = ride("66", "tram", gronau, bonnHbf, "20:14", "20:21", "66-a", "Siegburg Bf")
    private val rb = ride("RB26", "regional", bonnHbf, deutz, "20:30", "20:55", "rb26", "Köln Messe/Deutz")
    private val journey = Journey(listOf(tram, rb))
    private val change = journeyChanges(journey).single()

    private fun offer(vararg options: Journey) = Alternatives(
        stopId = bonnHbf.id, stopName = bonnHbf.name, keepLegs = change.keepLegs, arrival = tram.arrival,
        loading = false, options = options.toList(),
    )

    @Test
    fun aLaterTramOfTheSameLineIsNoConnection() {
        val next66 = Journey(listOf(ride("66", "tram", bonnHbf, siegburg, "20:27", "20:47", "66-b", "Siegburg Bf")))
        val other = Journey(listOf(ride("RE5", "regional", bonnHbf, deutz, "20:40", "21:02", "re5", "Emmerich")))
        val options = connectionOptions(change, offer(Journey(listOf(rb)), next66, other))
        assertEquals(listOf("RB26", "RE5"), options.map { it.rides.first().line })
        assertFalse(options.any { it.rides.first().line == "66" })
    }

    @Test
    fun theChosenDepartureStandsForItsFastestWayOnAndTheChangeKnowsTheArrival() {
        val slow = Journey(listOf(rb, ride("S12", "suburban", deutz, siegburg, "21:00", "21:18", "s12", "Au")))
        val fast = Journey(listOf(rb))
        val options = connectionOptions(change, offer(slow, fast))
        assertEquals(1, options.size)
        assertEquals(at("20:55"), options.single().arrival)
        assertEquals(journey.arrival, change.journeyArrival)
        assertTrue(change.journeyArrival != null)
    }
}
