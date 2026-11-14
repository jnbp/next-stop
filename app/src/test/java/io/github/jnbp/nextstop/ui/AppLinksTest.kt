package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.PlaceKind
import io.github.jnbp.nextstop.data.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLinksTest {
    private fun parse(link: String): OpenLink? {
        val rest = link.removePrefix("nextstop://open")
        val path = rest.substringBefore('?')
        val query = if ('?' in rest) rest.substringAfter('?') else null
        return parseOpenLink(path, queryParams(query))
    }

    @Test
    fun routeOfTheWebPageIsRead() {
        // As web/route writes it (URLSearchParams: a space is "+", a "+" is "%2B").
        val link = parse("nextstop://open/route?fromName=S%2BU+Alexanderplatz&from=52.52151%2C13.41127&toName=Flughafen+BER&to=52.36443%2C13.5097")
        val route = link as OpenLink.Route
        assertEquals("S+U Alexanderplatz", route.from?.name)
        assertEquals(52.52151, route.from?.latitude!!, 1e-9)
        assertEquals("Flughafen BER", route.to.name)
        assertEquals(13.5097, route.to.longitude!!, 1e-9)
        assertEquals(PlaceKind.Address, route.to.kind)
        assertTrue(route.to.id.startsWith("pin:"))
    }

    @Test
    fun routeWithoutStartStartsWhereTheUserIs() {
        val route = parse("nextstop://open/route?toName=Flughafen+BER&to=52.36443,13.5097") as OpenLink.Route
        assertNull(route.from)
    }

    @Test
    fun routeWithoutDestinationIsRefused() {
        assertNull(parse("nextstop://open/route?fromName=A&from=52.5,13.4"))
        assertNull(parse("nextstop://open/route?toName=Somewhere&to=91,13.4"))
        assertNull(parse("nextstop://open/route?toName=Somewhere&to=abc"))
    }

    @Test
    fun routeLinkRoundTripsAStop() {
        val stop = Stop("900100003", "S+U Alexanderplatz (Berlin)", 52.52151, 13.41127, 120, kind = PlaceKind.Stop)
        val text = routeLinkText(null, stop)
        assertTrue(text.startsWith("nextstop://open/route?"))
        val route = parse(text) as OpenLink.Route
        assertNull(route.from)
        assertEquals(stop.copy(distanceMeters = null), route.to)
    }

    @Test
    fun ownPositionAndPinsKeepOnlyCoordinates() {
        val here = Stop(CURRENT_LOCATION_ID, "Current location", 52.5, 13.4, null, kind = PlaceKind.Address)
        val pin = Stop("pin:52.4,13.3", "Near Somewhere", 52.4, 13.3, null, kind = PlaceKind.Address)
        val route = parse(routeLinkText(here, pin)) as OpenLink.Route
        assertNull(route.from)
        assertEquals("pin:52.4,13.3", route.to.id)
        assertEquals("Near Somewhere", route.to.name)
    }

    @Test
    fun departuresOfAStop() {
        val stop = Stop("de:12073:900", "Bahnhof & Markt", 53.0, 13.9, null, kind = PlaceKind.Stop)
        val link = parse(departuresLinkText(stop)) as OpenLink.Departures
        assertEquals(stop, link.stop)
    }

    @Test
    fun departuresWithoutStopAreHere() {
        assertEquals("nextstop://open/departures", departuresLinkText(null))
        assertEquals(OpenLink.Departures(null), parse("nextstop://open/departures"))
    }

    @Test
    fun unknownPathIsRefused() {
        assertNull(parse("nextstop://open/somewhere?x=1"))
        assertNull(parse("nextstop://open"))
    }

    @Test
    fun latLonNeedsTwoNumbers() {
        assertEquals(52.5 to 13.4, parseLatLon(" 52.5 , 13.4 "))
        assertNull(parseLatLon("52.5"))
        assertNull(parseLatLon("NaN,13.4"))
    }
}
