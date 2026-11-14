package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which notices lie within the radius around a place or the spots of a route (see disruptionsWithin, routeSpots). */
class PlaceRadiusTest {
    private val home = 52.5400 to 13.4120

    // About 220 m north of the place, and across the city.
    private val near = Stop("near", "U Eberswalder Str. (Berlin)", 52.5420, 13.4120, 220)
    private val far = Stop("far", "S+U Rathaus Spandau (Berlin)", 52.5354, 13.2000, null)

    private fun notice(id: String, summary: String, text: String, vararg stops: Stop) =
        Disruption(id, summary, text, setOf("M10"), setOf("tram"), stops = stops.toList())

    @Test
    fun aLineThatOnlyPassesByDoesNotCount() {
        // Carried by the departures of the stop nearby, but it lies at the other end of the city.
        val elsewhere = notice("1", "Umleitung", "Bus 136: Umleitung an der Haltestelle S+U Rathaus Spandau.", near)
        assertTrue(disruptionsWithin(home, 500, listOf(near), listOf(elsewhere)).isEmpty())
        // Without a stop named at all it says nothing about the place either.
        val vague = notice("2", "Bauarbeiten", "Tram M10: Ersatzverkehr mit Bussen.", near)
        assertTrue(disruptionsWithin(home, 500, listOf(near), listOf(vague)).isEmpty())
    }

    @Test
    fun aNoticeNamingAStopWithinTheRadiusCountsThere() {
        // Carried elsewhere, named here: "Straße" for "Str.", without "U" and "(Berlin)".
        val here = notice("3", "Umleitung", "Tram M10: Keine Haltestelle Eberswalder Straße.", far)
        val kept = disruptionsWithin(home, 500, listOf(near), listOf(here)).single()
        assertEquals(listOf(near), kept.stops)
        // In a smaller radius the stop lies outside.
        assertTrue(disruptionsWithin(home, 100, listOf(near), listOf(here)).isEmpty())
    }

    @Test
    fun aNameCountsOnlyAsWholeWords() {
        val stop = near.copy(name = "Eberswalder Strasse")
        assertTrue(stopsNamedIn("Bus 999: Umleitung in der Eberswalder Strasseneck-Allee.", listOf(stop)).isEmpty())
        assertEquals(listOf(stop), stopsNamedIn("Bus 999: Umleitung ab Eberswalder Strasse.", listOf(stop)))
    }

    @Test
    fun aStationsNoticeCountsAtItsStation() {
        val lift = notice("5", "Aufzug außer Betrieb", "Der Aufzug zum Bahnsteig ist außer Betrieb.", near)
        assertEquals(listOf(near), disruptionsWithin(home, 500, listOf(near), listOf(lift)).single().stops)
        val farLift = notice("6", "Aufzug außer Betrieb", "Der Aufzug zum Bahnsteig ist außer Betrieb.", far)
        assertTrue(disruptionsWithin(home, 500, listOf(near), listOf(farLift)).isEmpty())
    }

    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T10:00:00+01:00")

    private fun ride(from: Stop, to: Stop) = Leg(
        "M10", "tram", false, from.name, to.name, start, start, start, start, null, null, null, false,
        fromPlace = from, toPlace = to,
    )

    @Test
    fun aRouteIsWatchedAtItsEndsAndChanges() {
        val address = Stop("a", "Musterweg 1", home.first, home.second, null, kind = PlaceKind.Address)
        val boarding = Stop("b", "Boarding", 52.5405, 13.4120, null) // 55 m from the start: the same spot
        val change = Stop("c", "Change", 52.5200, 13.3800, null)
        val changeOther = Stop("c2", "Change", 52.5201, 13.3801, null) // another platform of the change
        val end = Stop("d", "End", 52.5000, 13.3000, null)
        val journey = Journey(listOf(ride(boarding, change), ride(changeOther, end)))
        val spots = routeSpots(SavedRoute(address, end), journey, 500)
        assertEquals(listOf("a", "d", "c"), spots.map { it.id })
        // Without a journey: its two ends.
        assertEquals(listOf("a", "d"), routeSpots(SavedRoute(address, end), null, 500).map { it.id })
    }

    @Test
    fun aNoticeAroundARouteThatIsOnItsRidesIsKeptOnce() {
        val onRide = notice("7", "Umleitung", "Tram M10: Keine Haltestelle Eberswalder Straße.", far)
        val aroundOnly = notice("8", "Aufzug außer Betrieb", "Der Aufzug ist außer Betrieb.", near)
        // The same notice around a spot: a copy of the one on the ride.
        val around = disruptionsWithin(home, 500, listOf(near), listOf(onRide.copy(stops = listOf(near)), aroundOnly))
        assertEquals(2, around.size)
        assertEquals(listOf("8"), withoutCopiesOf(around, listOf(onRide)).map { it.id })
    }
}
