package io.github.jnbp.nextstop.wear.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RescueOfferTest {
    private val phone = """{"active":true,"kind":"GetOff","place":"Example Square","fromPhone":true,
        "rescue":{"key":"j1#2#o7","line":"S7","product":"suburban","dep":1795000000000,"platform":"3","stop":"Example Square","color":4278229327}}"""

    @Test
    fun theOfferOfThePhoneIsRead() {
        val offer = TripSnapshot.parse(phone).rescue!!
        assertEquals("j1#2#o7", offer.key)
        assertEquals("S7", offer.line)
        assertEquals(1795000000000L, offer.departure)
        assertEquals("3", offer.platform)
        assertEquals("Example Square", offer.stop)
        assertEquals(4278229327L, offer.color)
        // Kept as the watch stores the state, and the key goes back with "Wechseln".
        assertEquals(offer, TripSnapshot.parse(TripSnapshot.parse(phone).toJson()).rescue)
        assertEquals("j1#2#o7", JSONObject(String(RescueOffer.switchPayload(offer))).getString("key"))
    }

    @Test
    fun withoutKeyOrDepartureNoOffer() {
        assertNull(RescueOffer.parse(JSONObject("""{"line":"S7","dep":1795000000000}""")))
        assertNull(RescueOffer.parse(JSONObject("""{"key":"k"}""")))
        assertNull(TripSnapshot.parse("""{"active":true,"kind":"Board"}""").rescue)
    }
}
