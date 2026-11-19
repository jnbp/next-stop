package io.github.jnbp.nextstop.wear.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The colour of a line on the watch is the phone's where the phone sent one: with the journeys
 * and departures it finds for the watch, as with its trip states. A tram of another town, or a
 * U1 outside Berlin, is not painted in the colours of Berlin's network map.
 */
class PhoneLineColorTest {
    private val minute = 60_000L
    private val tram = 0xFF00A0E0L

    private fun leg(line: String?, walk: Boolean, dep: Long, arr: Long, color: Long?) = JSONObject().apply {
        put("line", line ?: JSONObject.NULL)
        put("product", if (walk) "" else "tram")
        put("walk", walk)
        put("from", "A")
        put("to", "B")
        put("dep", dep * minute)
        put("arr", arr * minute)
        put("cancelled", false)
        // As the phone puts it: an Int of toArgb(), negative for an opaque colour.
        color?.let { put("color", it.toInt()) }
    }

    private val journey = parseJourneyJson(
        JSONObject().put(
            "legs",
            JSONArray()
                .put(leg(null, true, 1000, 1005, null))
                .put(leg("1", false, 1005, 1015, tram))
                .put(leg("2", false, 1020, 1030, 0xFFE2001AL)),
        ),
    )!!

    @Test
    fun aJourneyOfThePhoneKeepsItsColours() {
        assertNull(journey.legs[0].color)
        assertEquals(tram, journey.legs[1].color)
        assertEquals(tram, lineArgb(journey.legs[1].line, journey.legs[1].product, journey.legs[1].color))
    }

    @Test
    fun aTripFollowedOnTheWatchShowsThem() {
        // Before the first ride, at the change and in the overview.
        assertEquals(tram, snapshotOf(journey, 1001 * minute).color)
        val riding = snapshotOf(journey, 1010 * minute)
        assertEquals(tram, riding.color)
        assertEquals(0xFFE2001AL, riding.next?.color)
        assertEquals(listOf(tram, 0xFFE2001AL), journeyPlan(journey, "Berlin").rides.map { it.color })
    }

    @Test
    fun aReloadKeepsThem() {
        val fresh = Journey(journey.legs.map { it.copy(color = null) }, "token")
        assertEquals(listOf(null, tram, 0xFFE2001AL), withSearchFacts(fresh, journey).legs.map { it.color })
    }

    @Test
    fun departuresOfThePhoneKeepTheirColour() {
        val list = JSONArray().put(JSONObject().put("line", "M10").put("product", "tram").put("dir", "X").put("cancelled", false).put("color", tram.toInt()))
        assertEquals(tram, parseDeparturesJson(list).single().color)
        assertNull(parseDeparturesJson(JSONArray().put(JSONObject().put("line", "M10").put("product", "tram").put("dir", "X"))).single().color)
    }
}
