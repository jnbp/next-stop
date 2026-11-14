package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where notices lie on the main maps, one marker per stop, and the notices hidden from them (see MapNotices.kt). */
class MapNoticesTest {
    private val main = Stop("hbf", "Leipzig Hbf", 51.3450, 12.3820, null, setOf("regional", "suburban", "tram"))
    private val market = Stop("market", "Lindenauer Markt", 51.3380, 12.3330, null, setOf("tram", "bus"))
    // The same name in another city, far beyond the area of the notice.
    private val otherMarket = Stop("other", "Lindenauer Markt", 51.0500, 13.7400, null, setOf("bus"))
    private val hall = Stop("hall", "Rathaus", 51.3300, 12.3700, null, setOf("bus"))
    private val hallNorth = Stop("hallNorth", "Rathaus Gohlis", 51.3600, 12.3680, null, setOf("tram"))

    private fun notice(id: String, summary: String, text: String, vararg stops: Stop) =
        Disruption(id, summary, text, setOf("15"), setOf("tram"), stops = stops.toList())

    @Test
    fun aStationsNoticeLiesAtTheStopThatCarriesIt() {
        val lift = notice("1", "Aufzug außer Betrieb", "Der Aufzug zum Bahnsteig ist außer Betrieb.", main)
        assertEquals(listOf(main), noticePlaces(lift, NamedStops(listOf(market))))
    }

    @Test
    fun aNoticeLiesAtTheStopsItNamesNearby() {
        val diversion = notice("2", "Umleitung", "Tram 15: Keine Haltestelle Lindenauer Markt.", main)
        assertEquals(listOf(market), noticePlaces(diversion, NamedStops(listOf(market, otherMarket))))
        // Only a stop of that name far away: no place.
        assertTrue(noticePlaces(diversion, NamedStops(listOf(otherMarket))).isEmpty())
    }

    @Test
    fun aNoticeOfAWholeLineWithoutAPlaceHasNoMarker() {
        val line = notice("3", "Bauarbeiten", "Tram 15: Ersatzverkehr mit Bussen.", main)
        assertTrue(noticePlaces(line, NamedStops(listOf(market, hall))).isEmpty())
        // Nor one without a stop that carries it.
        val loose = notice("4", "Umleitung", "Tram 15: Keine Haltestelle Lindenauer Markt.")
        assertTrue(noticePlaces(loose, NamedStops(listOf(market))).isEmpty())
    }

    @Test
    fun aNameWithinALongerNameIsThatStop() {
        val diversion = notice("5", "Umleitung", "Bus 80: Umleitung ab Rathaus Gohlis.", main)
        assertEquals(listOf(hallNorth), noticePlaces(diversion, NamedStops(listOf(hall, hallNorth))))
    }

    @Test
    fun theNoticesOfOneStopAreOneMarker() {
        val a = notice("a", "Aufzug außer Betrieb", "Der Aufzug ist außer Betrieb.", main)
        val b = notice("b", "Umleitung", "Bus 60: Keine Haltestelle Leipzig Hbf.", main)
        // A part of the same station a little apart, of the same name.
        val platform = main.copy(id = "hbf2", latitude = 51.3470)
        val c = notice("c", "Fahrtreppe außer Betrieb", "Die Fahrtreppe ist außer Betrieb.", platform)
        val markers = groupNoticePlaces(listOf(a to main, b to main, c to platform, a to main))
        val marker = markers.single()
        assertEquals(listOf("a", "b", "c"), marker.items.map { it.id })
        // Another stop close by with another name stays a marker of its own.
        val near = Stop("near", "Wintergartenstraße", 51.3470, 12.3820, null)
        assertEquals(2, groupNoticePlaces(listOf(a to main, b to near)).size)
    }

    @Test
    fun hiddenNoticesGetNoMarker() {
        val a = notice("a", "Aufzug außer Betrieb", "Der Aufzug ist außer Betrieb.", main)
        val b = notice("b", "Umleitung", "Tram 15: Keine Haltestelle Lindenauer Markt.", main)
        val names = NamedStops(listOf(market))
        assertEquals(2, noticeMarkers(listOf(a, b), names, emptySet()).size)
        assertEquals(listOf("b"), noticeMarkers(listOf(a, b), names, setOf("a")).flatMap { m -> m.items.map { it.id } })
    }

    @Test
    fun aMarkerShowsTheLineOfItsNotices() {
        val one = notice("1", "Umleitung", "Tram 15: Umleitung.", main)
        val other = notice("2", "Umleitung", "Bus 60: Umleitung.", main)
        assertEquals("15", NoticeMarker("k", main, listOf(one)).badge?.line)
        assertNull(NoticeMarker("k", main, listOf(one, other)).badge)
    }

    @Test
    fun hiddenNoticesAreDroppedOnceLongUnseen() {
        val day = 24 * 60 * 60 * 1000L
        val now = 100 * day
        val hidden = listOf(
            HiddenNotice("seen", "A", lastSeen = now - 9 * day),
            HiddenNotice("recent", "B", lastSeen = now - 2 * day),
            HiddenNotice("old", "C", lastSeen = now - 6 * day),
        )
        val pruned = prunedHidden(hidden, setOf("seen"), now, 5 * day)
        assertEquals(listOf("seen", "recent"), pruned.map { it.id })
        assertEquals(now, pruned.first().lastSeen)
    }

    @Test
    fun theBigStopsInViewAreAsked() {
        val box = listOf(51.30, 12.30, 51.40, 12.45)
        val offline = Stop(OSM_STOP_PREFIX + "s:1", "Leipzig Markt", 51.3400, 12.3750, null, setOf("suburban"))
        val outside = Stop("out", "Grünau", 51.3100, 12.2800, null, setOf("suburban"))
        val picked = bigStopsIn(listOf(hall, market, offline, outside, main), box, 51.34 to 12.37)
        assertEquals(listOf(main, market, hall), picked)
        assertEquals(listOf(main), bigStopsIn(listOf(hall, market, main), box, 51.34 to 12.37, max = 1))
    }

    @Test
    fun aNoticeOfADepartureWithoutAStopGetsTheStopOfTheBoard() {
        val departure = Departure(
            "15", "tram", "Meusdorf", null, null, null, null, false,
            warnings = listOf(Warning("Umleitung", "Tram 15: Keine Haltestelle Lindenauer Markt.")),
        )
        val found = departureNotices(listOf(departure), main).single()
        assertEquals(listOf(main), found.stops)
        assertEquals(listOf(market), noticePlaces(found, NamedStops(listOf(market))))
    }
}
