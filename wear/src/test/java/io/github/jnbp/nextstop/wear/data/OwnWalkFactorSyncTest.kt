package io.github.jnbp.nextstop.wear.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "Eigener Faktor" of "Langsamer unterwegs" (#198) as the phone syncs it to the watch. */
class OwnWalkFactorSyncTest {
    @Test
    fun theOwnFactorSetsThePaceOfTransitous() {
        val options = PhoneSearchOptions.parse(JSONObject().put("walkingSpeed", "slow").put("walkFactor", 1.6))
        assertEquals(1.6, options.walkFactor!!, 1e-9)
        assertEquals("&pedestrianSpeed=0.75", options.motisQuery())
        // BVG knows only slow, normal and fast: the phone sends the nearest.
        assertEquals("&walkingSpeed=slow", options.hafasQuery())
    }

    @Test
    fun withoutAnOwnFactorNothingChanges() {
        val options = PhoneSearchOptions.parse(JSONObject().put("walkingSpeed", "slow"))
        assertNull(options.walkFactor)
        assertEquals("&pedestrianSpeed=0.9", options.motisQuery())
        assertEquals("", PhoneSearchOptions.parse(JSONObject().put("walkFactor", 1.0)).motisQuery())
    }

    @Test
    fun aBrokenFactorIsLeftOut() {
        assertNull(PhoneSearchOptions.parse(JSONObject().put("walkFactor", 40.0)).walkFactor)
        assertNull(PhoneSearchOptions.parse(JSONObject().put("walkFactor", "x")).walkFactor)
    }
}
