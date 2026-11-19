package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedBoardingSpotTest {
    @Test
    fun theSpotOnTheRideWinsAndNothingElseIsAsked() {
        var asked = 0
        val spot = sharedBoardingSpot("Pos. 4", { asked++; "Pos. 2" }) { asked++; "Pos. 3" }
        assertEquals("Pos. 4", spot)
        assertEquals(0, asked)
    }

    @Test
    fun withoutAPlatformNumberTheLineChoiceIsTaken() {
        // The case of #210: no platform from the provider, so no numbered position; the
        // position OpenStreetMap lists the line at (as on the big map) is the walk's target.
        assertEquals("Pos. 2", sharedBoardingSpot(null, { "Pos. 2" }) { null })
    }

    @Test
    fun theNumberedPositionIsTheLastResort() {
        assertEquals("Pos. 3", sharedBoardingSpot<String>(null, { null }) { "Pos. 3" })
        assertNull(sharedBoardingSpot<String>(null, { null }) { null })
    }

    @Test
    fun transitousLeavesOutMostBusAndTramPlatforms() {
        // Over the recorded departures: how many bus and tram boardings come without a platform,
        // the ones that had no stop position for the walk before #210.
        val rides = REAL_STOP_TIMES.filter { it.mode == "BUS" || it.mode == "TRAM" }
        val missing = rides.count { it.track == null }
        assertEquals(81, rides.size)
        assertEquals(51, missing)
    }
}
