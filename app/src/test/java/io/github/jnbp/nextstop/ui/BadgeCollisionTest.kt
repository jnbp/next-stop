package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The vehicle badges lie below the stop signs, which are placed first: a badge that did not
 * allow overlap was culled by every sign, name or pill placed before it, and buses and trams
 * at a stop vanished from the map.
 */
class BadgeCollisionTest {
    @Test
    fun vehiclesAreNeverCulledBySymbolsPlacedBeforeThem() {
        assertTrue(BADGE_COLLISION.allowOverlap)
    }

    @Test
    fun vehiclesTakeTheirRoomSoPillsAndDetailsBelowGiveWay() {
        assertFalse(BADGE_COLLISION.ignorePlacement)
    }
}
