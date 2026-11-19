package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** #157: a station keeps one name on a page: the railway's, where a long-distance ride shows it so. */
class PageNamesTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-11-18T${clock}:00+01:00")

    private fun ride(line: String, product: String, from: String, to: String, departure: String, arrival: String) = Leg(
        line, product, false, from, to, at(departure), at(departure), at(arrival), at(arrival),
        null, null, null, false, tripId = "$line-$departure",
    )

    private val ice = ride("ICE 374", "express", "Halle (Saale) Hbf", "Berlin Hbf", "14:20", "15:23")
    private val s7 = ride("S7", "suburban", "S+U Berlin Hauptbahnhof", "S Potsdam Hauptbahnhof", "15:27", "16:05")

    @Test
    fun theLocalNameOfAStationTheTrainNamesTakesTheTrainsName() {
        val journey = Journey(listOf(ice, s7))
        assertEquals("Berlin Hbf", journeyStopName(journey, "S+U Berlin Hauptbahnhof"))
        assertEquals("Berlin Hbf", journeyStopName(journey, "Berlin Hbf"))
    }

    @Test
    fun otherStationsKeepTheirPlainName() {
        val journey = Journey(listOf(ice, s7))
        // Not called at by the long-distance ride: the plain name, as before.
        assertEquals(journeyStopName(Journey(listOf(s7)), "S Potsdam Hauptbahnhof"), journeyStopName(journey, "S Potsdam Hauptbahnhof"))
        // Without a long-distance ride nothing changes.
        assertEquals("S+U Berlin Hauptbahnhof".plainStopName(), journeyStopName(Journey(listOf(s7)), "S+U Berlin Hauptbahnhof"))
    }

    @Test
    fun theStepFromPlatformToPlatform() {
        assertEquals("Gleis 16 → Gleis 2", platformStep("Gleis 16", "Gleis 2"))
        assertEquals(null, platformStep("Gleis 16", "Gleis 16"))
        assertEquals(null, platformStep(null, "Gleis 2"))
    }

    @Test
    fun theStepOfTheTripNotificationNamesTheStationAsThePage() {
        val names = railwayNamesOf(Journey(listOf(ice, s7)))
        // Boarding the S7 at the station the ICE calls "Berlin Hbf".
        assertEquals("Berlin Hbf", pageRideStop(s7, "S+U Berlin Hauptbahnhof", names))
        assertEquals(shownRideStop(s7, "S Potsdam Hauptbahnhof"), pageRideStop(s7, "S Potsdam Hauptbahnhof", names))
        assertEquals(shownRideStop(s7, "S+U Berlin Hauptbahnhof"), pageRideStop(s7, "S+U Berlin Hauptbahnhof", emptySet()))
    }
}
