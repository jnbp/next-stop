package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * #19, #35e: the track field of Transitous held ids of stop points, shown as "Gleis 78" at
 * Bonn Hbf, "Gleis 79" at Köln/Bonn Flughafen (track 4 in truth), "Gleis 84" at Troisdorf,
 * "Gleis 85" at Köln Messe/Deutz, and "Gleis 13/16/18" for the tram and bus 66.
 */
class PlatformNamesTest {
    @Test
    fun idsOfStopPointsAreNoTracks() {
        assertNull(shownPlatform("78", "regional"))
        assertNull(shownPlatform("79", "suburban"))
        assertNull(shownPlatform("84", "regional"))
        assertNull(shownPlatform("85", "express"))
        assertNull(shownPlatform("78", null))
        assertNull(shownPlatform("de:05314:61908:3:78", "regional"))
        assertNull(plausiblePlatform("85", "express"))
    }

    @Test
    fun realTracksStay() {
        assertEquals(ShownPlatform(PlatformKind.Track, "4"), shownPlatform("4", "suburban"))
        assertEquals(ShownPlatform(PlatformKind.Track, "11"), shownPlatform("11", "express"))
        assertEquals(ShownPlatform(PlatformKind.Track, "4a"), shownPlatform("4a", "regional"))
        assertEquals(ShownPlatform(PlatformKind.Track, "11 A-C"), shownPlatform("11 A-C", "express"))
        // The S-Bahn tunnels below some main stations.
        assertEquals(ShownPlatform(PlatformKind.Track, "101"), shownPlatform("101", "suburban"))
        // A word in front goes: the label adds its own.
        assertEquals(ShownPlatform(PlatformKind.Track, "4"), shownPlatform("Gleis 4", "regional"))
        assertEquals("4", plausiblePlatform(" 4 ", "regional"))
    }

    @Test
    fun aTramOrBusStopsAtABay() {
        assertEquals(ShownPlatform(PlatformKind.Bay, "13"), shownPlatform("13", "tram"))
        assertEquals(ShownPlatform(PlatformKind.Bay, "16"), shownPlatform("16", "bus"))
        assertEquals(ShownPlatform(PlatformKind.Bay, "18"), shownPlatform("18", "tram"))
        assertEquals(ShownPlatform(PlatformKind.Bay, "A"), shownPlatform("A", "bus"))
        assertEquals(ShownPlatform(PlatformKind.Bay, "3"), shownPlatform("Steig 3", "bus"))
        // Beyond the bays any stop has: an id.
        assertNull(shownPlatform("250", "bus"))
    }

    @Test
    fun textsThatSayWhatTheyAreStay() {
        assertEquals(ShownPlatform(PlatformKind.Text, "Pos. 11"), shownPlatform("Pos. 11", "bus"))
        assertNull(shownPlatform("  ", "bus"))
        assertNull(shownPlatform(null, "regional"))
    }
}
