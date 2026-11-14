package io.github.jnbp.nextstop.ui

import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The route link ("So kommst du hin", web/route): the JSON of the app must be exactly what the
 * page reads. web/test/fixtures/route-v1.json holds it for a fixed connection; the page's tests
 * (node web/test/route.test.js) decode the same file and write it again with their encoder.
 */
class RouteLinkTest {
    private val t0 = 1794640320L

    private fun fixtureLegs() = listOf(
        RouteLeg(
            provider = null, trip = null, line = null, product = null, direction = null, walk = true,
            from = RoutePlace("Startstraße 1", null, 52.52, 13.405, t0),
            to = RoutePlace("S+U Alexanderplatz", "900100003", 52.52151, 13.41127, t0 + 240, entrance = "B"),
            meters = 180,
            shape = routeShape(listOf(52.52 to 13.405, 52.5208 to 13.4085, 52.52151 to 13.41127), 4.0),
        ),
        RouteLeg(
            provider = "bvg", trip = "1|31911|21|86|14112026", line = "S5", product = "suburban", direction = "Strausberg Nord", walk = false,
            from = RoutePlace("S+U Alexanderplatz", "900100003", 52.52151, 13.41127, t0 + 300, 2, platform = "2"),
            to = RoutePlace("S Ostkreuz", "900120003", 52.50303, 13.46902, t0 + 840, 3, platform = "3"),
            stopCount = 3, board = "m",
        ),
        RouteLeg(
            provider = "tr", trip = "de-DELFI_1234#5", line = "RE7", product = "regional", direction = "Frankfurt (Oder) \"Bhf\" ü€😀", walk = false,
            from = RoutePlace("S Ostkreuz", "de-DELFI_000:11", 52.50281, 13.46985, t0 + 1200, -1, platform = "Pos. 11", spot = "3"),
            to = RoutePlace("Flughafen BER", "de-DELFI_000:22", 52.36443, 13.5097, t0 + 2280),
            stopCount = 0, board = "r", boardEstimated = true, cancelled = true,
        ),
    )

    /** The fixture, from the module folder (Gradle) or the repository root. */
    private fun fixture(): String {
        val file = listOf("../web/test/fixtures/route-v1.json", "web/test/fixtures/route-v1.json").map(::File).first { it.exists() }
        return file.readText(Charsets.UTF_8).trim()
    }

    @Test
    fun jsonIsExactlyTheFixtureOfThePage() {
        val json = routeLinkJson(fixtureLegs(), "Flughafen BER, Terminal 1", 52.3655, 13.5101, t0 - 600)
        assertEquals(fixture(), json)
    }

    @Test
    fun fragmentRoundTripsThroughBase64Url() {
        val json = routeLinkJson(fixtureLegs(), "Flughafen BER, Terminal 1", 52.3655, 13.5101, t0 - 600)
        val encoded = base64Url(json)
        assertTrue(encoded.matches(Regex("[A-Za-z0-9_-]+")))
        assertEquals(json, String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8))
    }

    @Test
    fun polylineMatchesTheDecoderOfThePage() {
        // The same strings are checked against the JavaScript encoder in route.test.js.
        assertEquals("_yp_IgdypA_D{TmCiP", encodePolyline(listOf(52.52 to 13.405, 52.5208 to 13.4085, 52.52151 to 13.41127)))
        assertEquals("b_vmEaa|y[cmL}uP", encodePolyline(listOf(-33.86882 to 151.20929, -33.8 to 151.3)))
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", encodePolyline(listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453)))
    }

    @Test
    fun shapesStayShort() {
        // A straight line needs no shape: the page draws it between the two ends.
        assertNull(routeShape(listOf(52.5 to 13.4, 52.50005 to 13.40005, 52.5001 to 13.4001), 4.0))
        assertNull(routeShape(listOf(52.5 to 13.4, 52.6 to 13.5), 4.0))
        // A long winding way keeps its look in a few hundred characters.
        val long = (0 until 2000).map { i -> (52.5 + i * 0.0001) to (13.4 + Math.sin(i / 50.0) * 0.01) }
        val shape = routeShape(long, 25.0)
        assertNotNull(shape)
        assertTrue("shape of ${shape!!.length} chars", shape.length < 800)
        val simple = simplifyPath(long, 25.0)
        assertEquals(long.first(), simple.first())
        assertEquals(long.last(), simple.last())
    }

    @Test
    fun emptyFieldsAreLeftOut() {
        val leg = RouteLeg(
            provider = null, trip = null, line = " ", product = null, direction = null, walk = true,
            from = RoutePlace("A", "", null, null, 10, platform = ""),
            to = RoutePlace("B", null, 1.0, 2.0, 20),
            meters = 0, board = "x",
        )
        assertEquals(
            "{\"legs\":[{\"from\":{\"name\":\"A\",\"dep\":10},\"to\":{\"name\":\"B\",\"lat\":1,\"lon\":2,\"arr\":20},\"walk\":true}]," +
                "\"dest\":{\"name\":\"B\"},\"t\":5}",
            routeLinkJson(listOf(leg), "B", null, null, 5),
        )
    }
}
