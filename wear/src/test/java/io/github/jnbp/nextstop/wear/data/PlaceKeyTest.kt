package io.github.jnbp.nextstop.wear.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** The phone's station identity key survives the watch: read with a place and sent back with it. */
class PlaceKeyTest {
    @Test
    fun keyGoesRoundTrip() {
        val json = JSONObject("""{"id":"900000001","key":"osm:complex:42","name":"Example Square","lat":52.5,"lon":13.4,"kind":"stop"}""")
        val place = parsePlaceJson(json)!!
        assertEquals("osm:complex:42", place.key)
        val back = placeJson(place)
        assertEquals("osm:complex:42", back.getString("key"))
        assertEquals(place, parsePlaceJson(back))
    }

    @Test
    fun noKeyStaysWithout() {
        val place = parsePlaceJson(JSONObject("""{"id":"a1","name":"Example Square","kind":"poi"}"""))!!
        assertNull(place.key)
        assertFalse(placeJson(place).has("key"))
    }
}
