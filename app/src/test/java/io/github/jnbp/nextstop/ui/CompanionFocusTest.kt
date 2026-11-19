package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** The companion map's locate button and its chip (#104). */
class CompanionFocusTest {
    private val base = OffsetDateTime.of(2026, 3, 2, 8, 0, 0, 0, ZoneOffset.ofHours(1))

    private fun leg(fromMinute: Long, toMinute: Long, path: List<Pair<Double, Double>>) = Leg(
        line = "S1",
        product = "suburban",
        walking = false,
        from = "A",
        to = "B",
        departure = base.plusMinutes(fromMinute),
        plannedDeparture = null,
        arrival = base.plusMinutes(toMinute),
        plannedArrival = null,
        departurePlatform = null,
        arrivalPlatform = null,
        direction = null,
        cancelled = false,
        path = path,
    )

    @Test
    fun locateGoesToTheOwnPositionWhereKnown() {
        assertEquals(CompanionMapFocus.Own, focusOnLocate(true))
        assertEquals(CompanionMapFocus.Trip, focusOnLocate(false))
    }

    @Test
    fun theChipSwitchesBetweenOwnAndRoute() {
        assertEquals(CompanionMapFocus.Route, CompanionMapFocus.Own.toggled())
        assertEquals(CompanionMapFocus.Own, CompanionMapFocus.Route.toggled())
        assertEquals(CompanionMapFocus.Trip, CompanionMapFocus.Trip.toggled())
    }

    @Test
    fun theRouteStillToGoLeavesOutWhatIsDone() {
        val done = leg(0, 10, listOf(1.0 to 1.0, 1.1 to 1.1))
        val next = leg(12, 30, listOf(2.0 to 2.0, 2.1 to 2.1))
        val points = remainingRoute(Journey(legs = listOf(done, next)), base.plusMinutes(11), own = 0.5 to 0.5)
        assertEquals(listOf(0.5 to 0.5, 2.0 to 2.0, 2.1 to 2.1), points)
    }

    @Test
    fun aLongWayIsThinnedButKeepsItsEnds() {
        val path = (0 until 1000).map { it.toDouble() to 0.0 }
        val points = remainingRoute(Journey(legs = listOf(leg(0, 30, path))), base, own = null, limit = 100)
        assertTrue(points.size in 90..110)
        assertEquals(0.0 to 0.0, points.first())
        assertEquals(999.0 to 0.0, points.last())
    }

    @Test
    fun theRideUnderWayIsCutAtTheVehicle() {
        // A long ride from the far start, under way, then a walk and a short ride (#166).
        val longRide = leg(0, 300, (0..100).map { 50.0 + it * 0.02 to 8.0 + it * 0.05 })
        val walk = leg(300, 310, listOf(52.0 to 13.0, 52.001 to 13.001))
        val last = leg(312, 330, listOf(52.001 to 13.001, 52.05 to 13.1))
        val vehicle = 51.9 to 12.75
        val points = remainingRoute(Journey(legs = listOf(longRide, walk, last)), base.plusMinutes(290), own = null, at = vehicle)
        assertEquals(vehicle, points.first())
        // Nothing of the part already behind (the far start).
        assertTrue(points.none { it.first < 51.8 })
        assertEquals(52.05 to 13.1, points.last())
    }

    @Test
    fun withoutAPositionTheTimetableCutsTheRide() {
        val ride = leg(0, 100, (0..100).map { it.toDouble() to 0.0 })
        val points = remainingRoute(Journey(legs = listOf(ride)), base.plusMinutes(75), own = null)
        assertTrue(points.first().first in 74.0..76.0)
        assertEquals(100.0 to 0.0, points.last())
    }

    @Test
    fun aRideNotBegunIsWhole() {
        val ride = leg(10, 100, listOf(1.0 to 1.0, 2.0 to 2.0))
        assertEquals(listOf(1.0 to 1.0, 2.0 to 2.0), remainingRoute(Journey(legs = listOf(ride)), base, own = null))
    }
}
