package io.github.jnbp.nextstop.wear.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The walk of a change on the watch takes the minutes the phone counts (changeWalkMinutes there):
 * a provider that stretches the walk over the wait for the next ride gave 12 minutes "on foot"
 * on the watch's own trip pages, 3 on the phone's.
 */
class ChangeWalkTest {
    private val minute = 60_000L

    private fun leg(walk: Boolean, from: String, to: String, dep: Long, arr: Long, changeWalk: Int? = null) = JSONObject().apply {
        put("line", if (walk) JSONObject.NULL else "S1")
        put("product", if (walk) "" else "suburban")
        put("walk", walk)
        put("from", from)
        put("to", to)
        put("dep", dep * minute)
        put("arr", arr * minute)
        put("cancelled", false)
        changeWalk?.let { put("changeWalk", it) }
    }

    private fun journey(changeWalk: Int?) = parseJourneyJson(
        JSONObject().put(
            "legs",
            JSONArray()
                .put(leg(false, "S Stop A", "S Stop B", 1000, 1010))
                .put(leg(true, "S Stop B", "S Stop B [Bus]", 1010, 1022, changeWalk))
                .put(leg(false, "S Stop B [Bus]", "Stop C", 1022, 1030)),
        ),
    )!!

    @Test
    fun theChangeCardSaysThePhonesWalk() {
        val riding = snapshotOf(journey(3), 1005 * minute)
        assertEquals(3, riding.next?.walkMinutes)
        // Without the phone's minutes (a search of the watch itself): the provider's times.
        assertEquals(12, snapshotOf(journey(null), 1005 * minute).next?.walkMinutes)
    }

    @Test
    fun aReloadKeepsThem() {
        val known = journey(3)
        val fresh = Journey(known.legs.map { it.copy(changeWalkMinutes = null) }, "token")
        assertEquals(3, snapshotOf(withSearchFacts(fresh, known), 1005 * minute).next?.walkMinutes)
    }
}
