package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.FavoritePlace
import io.github.jnbp.nextstop.data.PlaceIcon
import io.github.jnbp.nextstop.data.PlaceKind
import io.github.jnbp.nextstop.data.Stop
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.Inflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live link, format v2 (web/live): the compact JSON of the app must be exactly what the page
 * reads. web/test/fixtures/live-v2.json holds it for a fixed trip; the page's tests (node
 * web/test/live.test.js) expand the same file, write it again with their encoder, and read
 * live-v2.deflate.txt, the file deflated by java.util.zip.Deflater(nowrap = true) as here.
 */
class LiveLinkTest {
    private val t0 = 1794640320L

    private fun place(name: String, id: String?, lat: Double, lon: Double, time: Long, delay: Long = 0) =
        LivePlace(name, id, lat, lon, time, delay)

    private fun walk(from: LivePlace, to: LivePlace) = LiveLeg(null, null, null, null, true, from, to)

    // The same trip as V2_INPUT in web/test/live.test.js: a walk from an address, the S5, a change
    // between two ids of Ostkreuz, the RE7 and a walk home.
    private fun fixtureLegs() = listOf(
        walk(place("Startstraße 1", null, 52.52, 13.405, t0), place("S+U Alexanderplatz", "900100003", 52.52151, 13.41127, t0 + 240)),
        LiveLeg(
            "bvg", "1|31911|21|86|14112026", "S5", "Strausberg Nord", false,
            place("S+U Alexanderplatz", "900100003", 52.52151, 13.41127, t0 + 300, 2),
            place("S Ostkreuz", "900120003", 52.50303, 13.46902, t0 + 840, 3),
        ),
        walk(place("S Ostkreuz", "900120003", 52.50303, 13.46902, t0 + 840), place("S Ostkreuz", "de-DELFI_000:11", 52.50281, 13.46985, t0 + 1020)),
        LiveLeg(
            "tr", "de-DELFI_1234#5", "RE7", "Frankfurt (Oder) \"Bhf\" ü€😀", false,
            place("S Ostkreuz", "de-DELFI_000:11", 52.50281, 13.46985, t0 + 1200, -1),
            place("Flughafen BER", "de-DELFI_000:22", 52.36443, 13.5097, t0 + 2280),
        ),
        walk(place("Flughafen BER", "de-DELFI_000:22", 52.36443, 13.5097, t0 + 2280), place("Zuhause", null, 52.366, 13.51, t0 + 2700)),
    )

    private fun fixtureJson() = liveLinkCompactJson(
        name = "Ana",
        legs = fixtureLegs(),
        destination = LivePlace("Zuhause", null, 52.366, 13.51, null, 0),
        created = t0 - 600,
        destinationKind = "h",
    )

    /** A file of web/test/fixtures, from the module folder (Gradle) or the repository root. */
    private fun fixture(name: String): String {
        val file = listOf("../web/test/fixtures/$name", "web/test/fixtures/$name").map(::File).first { it.exists() }
        return file.readText(Charsets.UTF_8).trim()
    }

    private fun inflateRaw(bytes: ByteArray): String {
        val inflater = Inflater(true)
        try {
            inflater.setInput(bytes)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buffer, 0, n)
            }
            return out.toString(Charsets.UTF_8.name())
        } finally {
            inflater.end()
        }
    }

    @Test
    fun jsonIsExactlyTheFixtureOfThePage() {
        assertEquals(fixture("live-v2.json"), fixtureJson())
    }

    @Test
    fun theStartOfTheTripIsNotInTheLink() {
        val json = fixtureJson()
        assertFalse(json.contains("Startstra"))
        assertFalse(json.contains("13.405"))
        // A start that is a stop names the first walk, still without a position of its own.
        val named = liveLinkCompactJson("Ana", fixtureLegs(), LivePlace("Zuhause", null, 52.366, 13.51, null, 0), t0, origin = "S Hackescher Markt")
        assertTrue(named.contains("\"o\":\"S Hackescher Markt\""))
        assertFalse(named.contains("\"k\""))
    }

    @Test
    fun deflatedJsonRoundTripsAndReadsTheFileOfThePageTests() {
        val json = fixtureJson()
        val packed = deflateRaw(json)
        assertTrue(packed.size < json.toByteArray(Charsets.UTF_8).size)
        assertEquals(json, inflateRaw(packed))
        assertEquals(json, inflateRaw(Base64.getUrlDecoder().decode(fixture("live-v2.deflate.txt"))))
    }

    @Test
    fun linkHasVersionLanguageAndData() {
        val link = siteLink(siteAddress(""), "live", fixtureJson(), "de")!!
        assertTrue(link.startsWith("https://nextstop.bapo.me/live/#v=2&l=de&d="))
        val data = link.substringAfter("&d=")
        assertTrue(data.matches(Regex("[A-Za-z0-9_-]+")))
        assertEquals(fixtureJson(), inflateRaw(Base64.getUrlDecoder().decode(data)))
        // No language, or no language code, leaves "l" out.
        assertTrue(siteLink(siteAddress(""), "live", "{}", null)!!.startsWith("https://nextstop.bapo.me/live/#v=2&d="))
        assertTrue(siteLink(siteAddress(""), "live", "{}", "de&x=1")!!.startsWith("https://nextstop.bapo.me/live/#v=2&d="))
    }

    @Test
    fun savedPlacesAreFoundByIdOrNearness() {
        val home = FavoritePlace(PlaceIcon.Home, place = Stop("A=2@O=Musterweg 1", "Musterweg 1", 52.4900, 13.4300, null, kind = PlaceKind.Address))
        val work = FavoritePlace(PlaceIcon.Work, place = Stop("900100003", "S+U Alexanderplatz", 52.52151, 13.41127, null))
        val unset = FavoritePlace(PlaceIcon.Star)
        val places = listOf(unset, home, work)
        assertEquals(work, savedPlaceAt(places, "900100003", null, null))
        // About 60 m away: the same place.
        assertEquals(home, savedPlaceAt(places, null, 52.4905, 13.4303))
        // About 300 m away: another place.
        assertNull(savedPlaceAt(places, null, 52.4927, 13.4300))
        assertNull(savedPlaceAt(places, null, null, null))
        assertNull(savedPlaceAt(places, CURRENT_LOCATION_ID, null, null))
    }
}
