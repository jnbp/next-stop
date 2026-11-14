package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedPlaceTest {
    @Test
    fun geoWithCoordinates() {
        val place = parseGeoUri("geo:52.3644,13.5097")!!
        assertEquals(52.3644 to 13.5097, place.position)
        assertEquals("", place.query)
    }

    @Test
    fun geoWithAddressOnly() {
        val place = parseGeoUri("geo:0,0?q=Hauptstra%C3%9Fe+5%2C+15806+Zossen")!!
        assertNull(place.position)
        assertEquals("Hauptstraße 5, 15806 Zossen", place.query)
    }

    @Test
    fun geoWithLabelledPoint() {
        val place = parseGeoUri("geo:0,0?q=52.5163,13.3777(Brandenburger%20Tor)&z=16")!!
        assertEquals(52.5163 to 13.3777, place.position)
        assertEquals("Brandenburger Tor", place.name)
        assertEquals("Brandenburger Tor", place.asPlace()?.name)
    }

    @Test
    fun geoWithUncertainty() {
        assertEquals(52.5 to 13.4, parseGeoUri("geo:52.5,13.4;u=35")?.position)
        assertNull(parseGeoUri("geo:0,0"))
    }

    @Test
    fun googleShortLinkUsesTheTextBeforeIt() {
        val text = "Brandenburger Tor\nPariser Platz, 10117 Berlin\nhttps://maps.app.goo.gl/AbCdEf123"
        val place = parseSharedText(text)!!
        assertEquals("Pariser Platz, 10117 Berlin", place.query)
        assertEquals("Brandenburger Tor", place.name)
        assertNull(place.position)
    }

    @Test
    fun googlePlaceLinkGivesTheExactPoint() {
        val text = "https://www.google.com/maps/place/Flughafen+BER/@52.36,13.50,15z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d52.3644!4d13.5097"
        val place = parseSharedText(text)!!
        assertEquals(52.3644 to 13.5097, place.position)
        assertEquals("Flughafen BER", place.name)
        assertEquals("Flughafen BER", place.query)
    }

    @Test
    fun googleQueryLink() {
        val place = parseSharedText("https://maps.google.com/?q=52.40,13.05")!!
        assertEquals(52.40 to 13.05, place.position)
        val search = parseSharedText("https://www.google.com/maps/search/?api=1&query=Markt+1+Potsdam")!!
        assertEquals("Markt 1 Potsdam", search.query)
        assertNull(search.position)
    }

    @Test
    fun placeShareOfThisApp() {
        // As PlaceCard.sharePlace writes it.
        val text = "Bahnhof Wünsdorf-Waldstadt\nAm Bahnhof 1, 15806 Zossen\ngeo:52.168200,13.468900\n" +
            "https://www.openstreetmap.org/?mlat=52.168200&mlon=13.468900#map=17/52.168200/13.468900"
        val place = parseSharedText(text)!!
        assertEquals(52.1682 to 13.4689, place.position)
        assertEquals("Am Bahnhof 1, 15806 Zossen", place.query)
        assertEquals("Bahnhof Wünsdorf-Waldstadt", place.name)
    }

    @Test
    fun plainAddress() {
        val place = parseSharedText("  Lindenallee 3, 14776 Brandenburg an der Havel ")!!
        assertEquals("Lindenallee 3, 14776 Brandenburg an der Havel", place.query)
        assertNull(place.position)
    }

    @Test
    fun nothingToSearch() {
        assertNull(parseSharedText("https://maps.app.goo.gl/AbCdEf123"))
        assertNull(parseSharedText("   "))
    }
}
