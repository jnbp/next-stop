package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavLinksTest {
    // geo:

    @Test
    fun geoPoint() {
        val place = parseNavLink("geo:52.5219,13.4132")!!
        assertEquals(52.5219 to 13.4132, place.position)
    }

    @Test
    fun geoAddressOnly() {
        val place = parseNavLink("geo:0,0?q=Alexanderplatz+1%2C+10178+Berlin")!!
        assertNull(place.position)
        assertEquals("Alexanderplatz 1, 10178 Berlin", place.query)
    }

    @Test
    fun geoLabelledPoint() {
        val place = parseNavLink("geo:52.5163,13.3777?q=52.5163,13.3777(Brandenburger%20Tor)")!!
        assertEquals(52.5163 to 13.3777, place.position)
        assertEquals("Brandenburger Tor", place.name)
    }

    @Test
    fun geoWithoutPlace() {
        assertNull(parseNavLink("geo:0,0"))
        assertNull(parseNavLink("geo:0,0?q="))
        assertNull(parseNavLink("geo:"))
    }

    // google.navigation:

    @Test
    fun navigationToAddress() {
        val place = parseNavLink("google.navigation:q=Invalidenstra%C3%9Fe+10557+Berlin")!!
        assertNull(place.position)
        assertEquals("Invalidenstraße 10557 Berlin", place.query)
    }

    @Test
    fun navigationToUnencodedText() {
        val place = parseNavLink("google.navigation:q=Hauptbahnhof Berlin&mode=w")!!
        assertEquals("Hauptbahnhof Berlin", place.query)
    }

    @Test
    fun navigationToPoint() {
        assertEquals(52.525 to 13.369, parseNavLink("google.navigation:q=52.525,13.369")!!.position)
        assertEquals(52.525 to 13.369, parseNavLink("google.navigation:ll=52.525,13.369&mode=w")!!.position)
        assertEquals(52.525 to 13.369, parseNavLink("GOOGLE.NAVIGATION://?q=52.525,13.369")!!.position)
    }

    @Test
    fun navigationTextWithPoint() {
        val place = parseNavLink("google.navigation:q=Hauptbahnhof&ll=52.525,13.369")!!
        assertEquals(52.525 to 13.369, place.position)
        assertEquals("Hauptbahnhof", place.name)
        assertEquals("Hauptbahnhof", place.asPlace()!!.name)
    }

    @Test
    fun navigationWithoutPlace() {
        assertNull(parseNavLink("google.navigation:"))
        assertNull(parseNavLink("google.navigation:q="))
        assertNull(parseNavLink("google.navigation:q=0,0"))
        assertNull(parseNavLink("google.navigation:q=95,200"))
        assertNull(parseNavLink("google.navigation:mode=w"))
    }

    // Web links of Google Maps

    @Test
    fun dirLinkWithDestinationParameter() {
        val place = parseNavLink("https://www.google.com/maps/dir/?api=1&destination=Friedrichstra%C3%9Fe%2C+Berlin&travelmode=transit")!!
        assertEquals("Friedrichstraße, Berlin", place.query)
        assertNull(place.position)
        val point = parseNavLink("https://www.google.com/maps/dir/?api=1&origin=52.5,13.4&destination=52.5219,13.4132")!!
        assertEquals(52.5219 to 13.4132, point.position)
    }

    @Test
    fun dirLinkTakesTheLastStop() {
        val place = parseNavLink(
            "https://www.google.com/maps/dir/Alexanderplatz,+Berlin/Zoologischer+Garten,+Berlin/@52.51,13.37,13z/data=!3m1!4b1",
        )!!
        assertEquals("Zoologischer Garten, Berlin", place.query)
        assertNull(place.position)
        val fromHere = parseNavLink("https://www.google.com/maps/dir//52.5219,13.4132/@52.52,13.41,15z")!!
        assertEquals(52.5219 to 13.4132, fromHere.position)
    }

    @Test
    fun dirLinkWithoutDestinationIsNoPlace() {
        // Only the middle of the map: that is not where the way leads.
        assertNull(parseNavLink("https://www.google.com/maps/dir/@52.52,13.41,15z"))
    }

    @Test
    fun daddrLink() {
        val place = parseNavLink("https://maps.google.com/maps?saddr=Alexanderplatz&daddr=Hauptbahnhof+Berlin")!!
        assertEquals("Hauptbahnhof Berlin", place.query)
        val last = parseNavLink("http://maps.google.com/maps?saddr=A&daddr=Alexanderplatz+to:Friedrichstra%C3%9Fe")!!
        assertEquals("Friedrichstraße", last.query)
    }

    @Test
    fun placeLinkWithExactPoint() {
        val place = parseNavLink(
            "https://www.google.com/maps/place/Brandenburger+Tor/@52.5162,13.3755,17z/data=!3m1!4b1!4m6!3m5!8m2!3d52.5163!4d13.3777",
        )!!
        assertEquals(52.5163 to 13.3777, place.position)
        assertEquals("Brandenburger Tor", place.name)
    }

    @Test
    fun placeLinkWithEncodedUmlauts() {
        val place = parseNavLink("https://www.google.com/maps/place/Bahnhof+Friedrichstra%C3%9Fe/@52.52,13.38,17z")!!
        assertEquals("Bahnhof Friedrichstraße", place.query)
        val raw = parseNavLink("https://www.google.de/maps/place/Schloss+Schönhausen/")!!
        assertEquals("Schloss Schönhausen", raw.query)
    }

    @Test
    fun searchLink() {
        val text = parseNavLink("https://www.google.com/maps/search/M%C3%BCggelsee/@52.43,13.64,13z")!!
        assertEquals("Müggelsee", text.query)
        assertNull(text.position)
        val point = parseNavLink("https://www.google.com/maps/search/52.5219,+13.4132")!!
        assertEquals(52.5219 to 13.4132, point.position)
        val api = parseNavLink("https://www.google.com/maps/search/?api=1&query=Hauptbahnhof+Berlin")!!
        assertEquals("Hauptbahnhof Berlin", api.query)
    }

    @Test
    fun queryLink() {
        assertEquals(52.40 to 13.05, parseNavLink("https://maps.google.com/?q=52.40,13.05")!!.position)
        assertEquals("Alexanderplatz", parseNavLink("https://maps.google.com/maps?q=Alexanderplatz&ftid=0x0:0x0")!!.query)
    }

    @Test
    fun centreOfTheMapOnly() {
        assertEquals(52.5219 to 13.4132, parseNavLink("https://www.google.com/maps/@52.5219,13.4132,15z")!!.position)
    }

    @Test
    fun consentWrappedLink() {
        val link = "https://consent.google.com/ml?continue=https://www.google.com/maps/place/Alexanderplatz/%3Fq%3Dx&gl=DE"
        assertEquals("https://www.google.com/maps/place/Alexanderplatz/?q=x", unwrapConsentLink(link))
        assertEquals(
            "Brandenburger Tor",
            parseNavLink("https://consent.google.com/m?continue=https%3A%2F%2Fwww.google.com%2Fmaps%2Fplace%2FBrandenburger%2BTor%2F")!!.query,
        )
    }

    @Test
    fun otherLinksAreNoPlace() {
        assertNull(parseNavLink("https://www.google.com/search?q=Alexanderplatz"))
        assertNull(parseNavLink("https://www.openstreetmap.org/#map=15/52.52/13.41"))
        assertNull(parseNavLink("https://www.google.com/mapsearch"))
        assertNull(parseNavLink("https://www.google.com/maps"))
        assertNull(parseNavLink("nextstop://open/route?to=52.5,13.4"))
        assertNull(parseNavLink(""))
        assertNull(parseNavLink("Alexanderplatz"))
        assertNull(parseNavLink("https://maps.app.goo.gl/AbCdEf123"))
    }

    @Test
    fun longTextIsCut() {
        val long = "A".repeat(500)
        assertEquals(160, parseNavLink("google.navigation:q=$long")!!.query.length)
    }

    // Short links

    @Test
    fun shortLinks() {
        assertTrue(isShortMapLink("https://maps.app.goo.gl/AbCdEf123"))
        assertTrue(isShortMapLink("http://goo.gl/maps/AbCdEf123"))
        assertFalse(isShortMapLink("https://maps.app.goo.gl/"))
        assertFalse(isShortMapLink("https://goo.gl/maps/"))
        assertFalse(isShortMapLink("https://goo.gl/AbCdEf"))
        assertFalse(isShortMapLink("https://www.google.com/maps/place/X"))
        assertFalse(isShortMapLink("geo:52.5,13.4"))
    }

    @Test
    fun mapsLinkFoundInPage() {
        val page = """<html><head><meta http-equiv="refresh" content="0; url=https://www.google.com/maps/place/Alexanderplatz/@52.52,13.41,17z?entry=tts&amp;g_st=x"></head></html>"""
        assertEquals("https://www.google.com/maps/place/Alexanderplatz/@52.52,13.41,17z?entry=tts&g_st=x", mapsLinkInPage(page))
        assertNull(mapsLinkInPage("<html>nothing</html>"))
    }
}
