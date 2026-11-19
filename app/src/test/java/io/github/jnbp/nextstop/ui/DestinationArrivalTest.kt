package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationArrivalTest {
    private val end = 52.5200 to 13.4050

    @Test
    fun theTripAtItsDestinationHasArrived() {
        assertTrue(destinationReached(tripArrived = true, own = null, end = end))
    }

    @Test
    fun theOwnPositionAtTheEndHasArrived() {
        // About 20 m north of the end.
        assertTrue(destinationReached(tripArrived = false, own = 52.52018 to 13.4050, end = end))
    }

    @Test
    fun aPositionStillOnTheWayHasNot() {
        // About 100 m away.
        assertFalse(destinationReached(tripArrived = false, own = 52.5209 to 13.4050, end = end))
        assertFalse(destinationReached(tripArrived = false, own = null, end = end))
        assertFalse(destinationReached(tripArrived = false, own = end, end = null))
    }
}
