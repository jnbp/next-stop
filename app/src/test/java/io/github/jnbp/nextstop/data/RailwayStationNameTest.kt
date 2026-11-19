package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** #93: on a long-distance ride the stations read as the railway names them (DB style). */
class RailwayStationNameTest {
    @Test
    fun mainStations() {
        assertEquals("Berlin Hbf", railwayStationName("S+U Berlin Hauptbahnhof"))
        assertEquals("Frankfurt (Main) Hbf", railwayStationName("Frankfurt (Main) Hauptbahnhof"))
        assertEquals("Hanau Hbf", railwayStationName("Hanau Hauptbahnhof"))
        assertEquals("Leipzig Hbf", railwayStationName("Leipzig, Hauptbahnhof"))
        assertEquals("Erfurt Hbf", railwayStationName("Erfurt Hbf"))
    }

    @Test
    fun otherStations() {
        assertEquals("Fulda", railwayStationName("Fulda Bahnhof"))
        assertEquals("Köln Messe/Deutz", railwayStationName("Köln Messe/Deutz Bf"))
        assertEquals("Berlin Südkreuz", railwayStationName("S Südkreuz Bhf (Berlin)"))
        assertEquals("Berlin Gesundbrunnen", railwayStationName("S+U Gesundbrunnen Bhf (Berlin)"))
        assertEquals("Frankfurt (Main) Flughafen Fernbf", railwayStationName("Frankfurt (Main) Flughafen Fernbahnhof"))
        assertEquals("Halle (Saale) Hbf", railwayStationName("Halle (Saale) Hbf"))
        assertEquals("Frankfurt (Oder)", railwayStationName("Frankfurt (Oder), Bahnhof"))
    }

    @Test
    fun onlyTheWordStaysAsItIs() {
        assertEquals("Hauptbahnhof", railwayStationName("Hauptbahnhof"))
    }

    @Test
    fun whatIsSentToTheWatchAndTheNotification() {
        val ice = Leg(
            "ICE 1092", "express", false, "Frankfurt (Main) Hauptbahnhof", "S+U Berlin Hauptbahnhof",
            java.time.OffsetDateTime.parse("2026-11-18T11:21:00+01:00"), null,
            java.time.OffsetDateTime.parse("2026-11-18T15:17:00+01:00"), null, null, null, "S+U Berlin Hauptbahnhof", false,
        )
        val bus = ice.copy(line = "M48", product = "bus", direction = "S+U Rathaus Steglitz")
        assertEquals("Berlin Hbf", shownRideStop(ice, ice.to))
        assertEquals("Berlin Hbf", railwayDirection(ice, ice.direction!!))
        assertEquals("S+U Rathaus Steglitz", railwayDirection(bus, bus.direction!!))
        assertEquals("Frankfurt (Main) Hauptbahnhof", shownRideStop(bus, "Frankfurt (Main) Hauptbahnhof"))
    }
}
