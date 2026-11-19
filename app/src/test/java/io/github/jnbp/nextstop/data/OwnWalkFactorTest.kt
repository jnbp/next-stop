package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "Eigener Faktor" of "Langsamer unterwegs" (#198). */
class OwnWalkFactorTest {
    @Test
    fun anOwnFactorStaysInItsRangeOnItsSteps() {
        assertEquals(0.8, ownWalkFactor(0.5), 1e-9)
        assertEquals(2.0, ownWalkFactor(3.7), 1e-9)
        assertEquals(1.35, ownWalkFactor(1.36), 1e-9)
        assertEquals(1.4, ownWalkFactor(1.38), 1e-9)
        assertEquals(SLOWER_WALK_FACTOR, ownWalkFactor(Double.NaN), 1e-9)
        // Stored as a Float and read back: still the same step.
        assertEquals(1.35, ownWalkFactor(1.35f.toDouble()), 1e-9)
    }

    @Test
    fun theOwnFactorWinsOverTheSwitch() {
        assertEquals(1.0, walkFactor(slower = false), 1e-9)
        assertEquals(SLOWER_WALK_FACTOR, walkFactor(slower = true), 1e-9)
        assertEquals(1.6, walkFactor(slower = true, own = 1.6), 1e-9)
        assertEquals(0.85, walkFactor(slower = false, own = 0.85), 1e-9)
        assertEquals(1.6, SearchOptions(slower = true, ownWalkFactor = 1.6).walkFactorInUse, 1e-9)
        // Without an own factor everything stays as before.
        assertEquals(SLOWER_WALK_FACTOR, SearchOptions(slower = true).walkFactorInUse, 1e-9)
    }

    @Test
    fun thePreviewOfFiveMinutes() {
        assertEquals(7, pacedWalkMinutes(5, 1.3))
        assertEquals(5, pacedWalkMinutes(5, 1.0))
        assertEquals(6, pacedWalkMinutes(5, 1.2))
        assertEquals(10, pacedWalkMinutes(5, 2.0))
        assertEquals(4, pacedWalkMinutes(5, 0.8))
        assertEquals(0, pacedWalkMinutes(0, 1.5))
    }

    @Test
    fun theProvidersAreAskedAtAPaceNearTheFactor() {
        assertEquals(WalkingSpeed.Slow, walkingSpeedFor(SLOWER_WALK_FACTOR))
        assertEquals(WalkingSpeed.Normal, walkingSpeedFor(1.1))
        assertEquals(WalkingSpeed.Fast, walkingSpeedFor(0.85))
        assertEquals(WalkingSpeed.Slow, SearchOptions(slower = true, ownWalkFactor = 1.8).walkingSpeed)
        assertEquals(WalkingSpeed.Normal, SearchOptions(slower = true, ownWalkFactor = 1.05).walkingSpeed)
        assertEquals("0.60", motisPedestrianSpeed(2.0))
        assertEquals("1.50", motisPedestrianSpeed(0.8))
        assertNull(motisPedestrianSpeed(1.0))
    }
}
