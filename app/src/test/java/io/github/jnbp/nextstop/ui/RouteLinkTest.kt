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

    @Test
    fun aWayByBikeSaysSo() {
        // The bike combo: the way to the first stop is ridden; the page then says so instead of "on foot".
        val ride = RouteLeg(
            provider = null, trip = null, line = null, product = null, direction = null, walk = true, bike = true,
            from = RoutePlace("A", null, null, null, 10),
            to = RoutePlace("B", null, null, null, 20),
        )
        assertEquals(
            "{\"legs\":[{\"from\":{\"name\":\"A\",\"dep\":10},\"to\":{\"name\":\"B\",\"arr\":20},\"walk\":true,\"bike\":true}]," +
                "\"dest\":{\"name\":\"B\"},\"t\":5}",
            routeLinkJson(listOf(ride), "B", null, null, 5),
        )
        // Only a way on the street can be ridden by bike.
        val train = ride.copy(walk = false, line = "S1")
        assertTrue(!routeLinkJson(listOf(train), "B", null, null, 5).contains("bike"))
    }

    /** A curve of [radius] metres through [degrees], one point per degree. */
    private fun curve(radius: Double, degrees: Int, startLat: Double = 52.5, startLon: Double = 13.4): List<Pair<Double, Double>> =
        (0..degrees).map { step ->
            val angle = Math.toRadians(step.toDouble())
            (startLat + radius * Math.sin(angle) / 111_320.0) to
                (startLon + radius * (1 - Math.cos(angle)) / (111_320.0 * Math.cos(Math.toRadians(startLat))))
        }

    /** The largest change of direction between two segments of [points], in degrees. */
    private fun sharpestTurn(points: List<Pair<Double, Double>>): Double {
        val k = Math.cos(Math.toRadians(points[0].first))
        val headings = points.zipWithNext { a, b -> Math.atan2(b.first - a.first, (b.second - a.second) * k) }
        return headings.zipWithNext { a, b ->
            var d = Math.toDegrees(b - a)
            while (d > 180) d -= 360
            while (d < -180) d += 360
            Math.abs(d)
        }.maxOrNull() ?: 0.0
    }

    @Test
    fun rideShapesKeepTheirCurvesRound() {
        // A ride through a curve of 300 m radius: no corner sharper than 25 degrees in the link.
        val shape = routeShape(curve(300.0, 90), RIDE_SHAPE_TOLERANCE_M, ROUTE_SHAPE_MAX_POINTS)!!
        assertTrue("turn of ${sharpestTurn(decodeForTest(shape))} degrees", sharpestTurn(decodeForTest(shape)) <= 25.0)
    }

    @Test
    fun aLongWindingRideStaysAReasonableLink() {
        // 20 km of rails that wind all the way (a point every 10 m): the link stays a few kilobytes.
        val winding = (0 until 2000).map { i -> (52.4 + i * 0.00009) to (13.3 + Math.sin(i / 40.0) * 0.004) }
        val shape = routeShape(winding, RIDE_SHAPE_TOLERANCE_M, ROUTE_SHAPE_MAX_POINTS)!!
        assertTrue("shape of ${shape.length} chars", shape.length < 2500)
        assertTrue(decodeForTest(shape).size <= ROUTE_SHAPE_MAX_POINTS)
    }

    /** A Google encoded polyline (precision 5) back to points, as decodePolyline in web/shared/core.js. */
    private fun decodeForTest(text: String): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        var index = 0
        var lat = 0L
        var lon = 0L
        fun next(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = text[index++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
                if (b < 0x20) break
            }
            return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        }
        while (index < text.length) {
            lat += next()
            lon += next()
            out.add(lat / 1e5 to lon / 1e5)
        }
        return out
    }
}
