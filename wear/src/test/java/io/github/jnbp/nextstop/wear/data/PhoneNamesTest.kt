package io.github.jnbp.nextstop.wear.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A journey the phone found for the watch names its stops as the phone's trip state does (#93
 * railway names of a long-distance ride, #168 short words of a regional train, #146 the short
 * destination), also while the watch follows it itself: no other names after the phone takes over.
 */
class PhoneNamesTest {
    private val minute = 60_000L

    private fun leg(product: String, walk: Boolean, from: String, to: String, dep: Long, arr: Long, shown: Map<String, String> = emptyMap()) =
        JSONObject().apply {
            put("line", if (walk) JSONObject.NULL else "ICE 1001")
            put("product", product)
            put("walk", walk)
            put("from", from)
            put("to", to)
            put("dep", dep * minute)
            put("arr", arr * minute)
            if (!walk) put("dir", "München Hbf")
            put("cancelled", false)
            shown.forEach { (key, value) -> put(key, value) }
        }

    private val journey = parseJourneyJson(
        JSONObject().put(
            "legs",
            JSONArray()
                .put(leg("", true, "Somewhere", "S+U Berlin Hauptbahnhof", 1000, 1010))
                .put(
                    leg(
                        "express", false, "S+U Berlin Hauptbahnhof", "Leipzig, Hauptbahnhof", 1015, 1080,
                        mapOf("fromShown" to "Berlin Hbf", "toShown" to "Leipzig Hbf"),
                    ),
                )
                .put(leg("", true, "Leipzig, Hauptbahnhof", "Museum, Ring 1, 04109 Leipzig", 1080, 1090, mapOf("toShown" to "Museum"))),
        ),
    )!!

    @Test
    fun theTripOfTheWatchNamesItsStopsAsThePhone() {
        val board = snapshotOf(journey, 1001 * minute)
        assertEquals("Berlin Hbf", board.place)
        assertEquals("Leipzig Hbf", board.rideTo)
        assertEquals("Museum", board.destination)
        val riding = snapshotOf(journey, 1030 * minute)
        assertEquals("Leipzig Hbf", riding.place)
        assertEquals(listOf("Berlin Hbf" to "Leipzig Hbf"), journeyPlan(journey, "Berlin").rides.map { it.from to it.to })
    }

    @Test
    fun theProvidersNamesStayForMatching() {
        assertEquals("S+U Berlin Hauptbahnhof", journey.legs[1].from)
        assertEquals("Museum, Ring 1, 04109 Leipzig", journey.legs.last().to)
    }

    @Test
    fun aReloadKeepsThem() {
        val fresh = Journey(journey.legs.map { it.copy(fromShown = null, toShown = null, directionShown = null) }, "token")
        val kept = withSearchFacts(fresh, journey)
        assertEquals("Berlin Hbf", kept.legs[1].fromShown)
        assertEquals("Museum", kept.legs.last().toShown)
        assertEquals("Museum", snapshotOf(kept, 1001 * minute).destination)
    }

    @Test
    fun aDirectionOfTheBoardAsThePhoneNamesIt() {
        val list = JSONArray()
            .put(JSONObject().put("line", "RE1").put("product", "regional").put("dir", "Frankfurt (Oder), Bahnhof").put("dirShown", "Frankfurt (Oder)"))
            .put(JSONObject().put("line", "M10").put("product", "tram").put("dir", "Warschauer Str."))
        assertEquals(listOf("Frankfurt (Oder)", "Warschauer Str."), parseDeparturesJson(list).map { it.direction })
    }
}
