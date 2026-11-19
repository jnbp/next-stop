package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApproachRoutesTest {
    @Test
    fun offDrawsNoApproachAndAsksNothing() {
        var asked = 0
        val rides = approachRides(listOf(1, 2, 3), shown = false) { asked++; true }
        assertTrue(rides.isEmpty())
        assertEquals(0, asked)
    }

    @Test
    fun onDrawsTheRidesStillAhead() {
        assertEquals(listOf(2, 3), approachRides(listOf(1, 2, 3), shown = true) { it > 1 })
    }

    @Test
    fun onWithoutRidesAheadDrawsNone() {
        assertTrue(approachRides(listOf(1, 2), shown = true) { false }.isEmpty())
    }

    @Test
    fun offShowsOnlyTheVehicleOfTheRideNow() {
        // Still in the first ride: the vehicle of the next one is not drawn yet (#160).
        assertEquals(listOf("ice"), vehicleRides(listOf("ice", "s7"), shown = false, current = "ice"))
        // Got off, on the way to the next one: now it is the traveller's vehicle.
        assertEquals(listOf("s7"), vehicleRides(listOf("s7"), shown = false, current = "s7"))
    }

    @Test
    fun offWithoutAStepTakesTheFirstRideStillToCome() {
        assertEquals(listOf("ice"), vehicleRides(listOf("ice", "s7"), shown = false, current = null))
        assertEquals(listOf("ice"), vehicleRides(listOf("ice", "s7"), shown = false, current = "gone"))
        assertTrue(vehicleRides(emptyList<String>(), shown = false, current = null).isEmpty())
    }

    @Test
    fun onShowsAllLaterVehiclesAsBefore() {
        assertEquals(listOf("ice", "s7"), vehicleRides(listOf("ice", "s7"), shown = true, current = "ice"))
    }
}
