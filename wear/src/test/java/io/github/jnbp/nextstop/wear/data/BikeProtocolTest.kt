package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A way ridden by bike (the bike combo of the phone) reaches the watch as such, not as a walk. */
class BikeProtocolTest {
    private val trip = """
        {"active":true,"kind":"WalkToEnd","place":"Stop Z","time":1,"arrival":2,"destination":"Stop Z",
         "walk":{"from":"Stop A","to":"Stop Z","fromLat":52.5,"fromLon":13.4,"toLat":52.51,"toLon":13.41,"minutes":12,"bike":true},
         "next":{"line":"S1","product":"suburban","stop":"Stop B","time":3,"walkMinutes":4,"bike":true},
         "plan":[{"line":"S1","product":"suburban","from":"Stop B","to":"Stop C","dep":3,"arr":4,"walkBefore":6,"bikeBefore":true}],
         "planWalkEnd":12,"planBikeEnd":true}
    """.trimIndent()

    @Test
    fun bikeWaysAreRead() {
        val snapshot = TripSnapshot.parse(trip)
        assertEquals(true, snapshot.walk?.cycling)
        assertEquals(true, snapshot.next?.cycling)
        assertTrue(snapshot.plan.single().bikeBefore)
        assertTrue(snapshot.planBikeEnd)
    }

    @Test
    fun bikeWaysSurviveTheStore() {
        val again = TripSnapshot.parse(TripSnapshot.parse(trip).toJson())
        assertEquals(true, again.walk?.cycling)
        assertEquals(true, again.next?.cycling)
        assertTrue(again.plan.single().bikeBefore)
        assertTrue(again.planBikeEnd)
    }

    @Test
    fun walksStayWalks() {
        val walking = TripSnapshot.parse(trip.replace(",\"bike\":true", "").replace(",\"bikeBefore\":true", "").replace(",\"planBikeEnd\":true", ""))
        assertEquals(false, walking.walk?.cycling)
        assertFalse(walking.plan.single().bikeBefore)
        assertFalse(walking.planBikeEnd)
    }
}
