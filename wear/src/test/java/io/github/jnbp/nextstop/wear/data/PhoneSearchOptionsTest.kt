package io.github.jnbp.nextstop.wear.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A search of the watch itself asks what the phone's searches ask: the walking pace ("Langsamer
 * unterwegs"), step-free ways and the products ("Nur Deutschlandticket"), as the phone syncs them.
 */
class PhoneSearchOptionsTest {
    private val localOnly = listOf("suburban", "subway", "tram", "bus", "ferry", "regional")

    private fun synced(options: JSONObject?): PhoneSearchOptions =
        SyncedPlaces.parse(JSONObject().put("favorites", JSONArray()).apply { options?.let { put("searchOptions", it) } }.toString()).phoneSearch

    @Test
    fun anOlderPhoneAppAsksNothingMore() {
        val none = synced(null)
        assertEquals("", none.hafasQuery())
        assertEquals("", none.motisQuery())
    }

    @Test
    fun slowerWalkingAndOnlyTheDeutschlandticket() {
        val options = synced(JSONObject().put("walkingSpeed", "slow").put("products", JSONArray(localOnly)))
        // BVG: as the phone's optionQuery and hafasProductQuery (coaches come as buses there).
        assertEquals("&walkingSpeed=slow&express=false", options.hafasQuery())
        assertEquals("&pedestrianSpeed=0.9&transitModes=SUBURBAN,SUBWAY,TRAM,BUS,FERRY,REGIONAL_RAIL,REGIONAL_FAST_RAIL", options.motisQuery())
    }

    @Test
    fun everythingAtANormalPaceAsksForNoProducts() {
        val options = synced(JSONObject().put("walkingSpeed", "normal").put("products", JSONArray(SEARCH_PRODUCT_KEYS)))
        assertEquals("&walkingSpeed=normal", options.hafasQuery())
        assertEquals("", options.motisQuery())
    }

    @Test
    fun stepFreeWays() {
        val options = synced(JSONObject().put("walkingSpeed", "normal").put("accessibility", "complete"))
        assertEquals("&walkingSpeed=normal&accessibility=complete", options.hafasQuery())
        assertEquals("&pedestrianProfile=WHEELCHAIR", options.motisQuery())
    }

    @Test
    fun withoutBusesNorCoaches() {
        val options = synced(JSONObject().put("products", JSONArray(listOf("suburban", "subway", "tram", "ferry", "regional", "express"))))
        assertEquals("&bus=false", options.hafasQuery())
    }
}
