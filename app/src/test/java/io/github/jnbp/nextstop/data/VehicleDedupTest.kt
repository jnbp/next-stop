package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One physical vehicle reported twice by the radar is drawn once (see distinctVehicles). */
class VehicleDedupTest {
    private val base = OffsetDateTime.of(2026, 11, 15, 18, 0, 0, 0, ZoneOffset.ofHours(1))

    private fun vehicle(
        tripId: String,
        line: String = "S42",
        lat: Double = 52.5450,
        lon: Double = 13.3880,
        next: String? = "S Wedding",
        nextInSeconds: Long? = 60,
        direction: String = "Ring",
        realtime: Boolean = false,
        product: String = "suburban",
        pathPoints: Int = 3,
    ) = Vehicle(
        tripId = tripId,
        line = line,
        product = product,
        direction = direction,
        path = List(pathPoints) { (lat + it * 0.0005) to lon },
        nextStop = next,
        nextStopTime = nextInSeconds?.let { base.plusSeconds(it) },
        realtime = realtime,
    )

    @Test
    fun aRingRunReportedUnderTwoTripIdsIsDrawnOnce() {
        val shown = distinctVehicles(listOf(vehicle("a"), vehicle("b", lat = 52.5455, nextInSeconds = 90)))
        assertEquals(1, shown.size)
        assertEquals("a", shown[0].tripId)
    }

    @Test
    fun theOneWithLiveDataIsKept() {
        val shown = distinctVehicles(listOf(vehicle("a"), vehicle("b", realtime = true)))
        assertEquals(listOf("b"), shown.map { it.tripId })
    }

    @Test
    fun theOneShownBeforeKeepsItsPlace() {
        val shown = distinctVehicles(listOf(vehicle("a"), vehicle("b")), shownBefore = setOf("b"))
        assertEquals(listOf("b"), shown.map { it.tripId })
    }

    @Test
    fun theSameTripTwiceIsOneVehicle() {
        assertEquals(1, distinctVehicles(listOf(vehicle("a"), vehicle("a", lat = 52.6))).size)
    }

    @Test
    fun otherLinesAndOtherProductsStay() {
        val shown = distinctVehicles(listOf(vehicle("a"), vehicle("b", line = "S41"), vehicle("c", product = "bus")))
        assertEquals(3, shown.size)
    }

    @Test
    fun vehiclesApartStay() {
        // About 550 m apart, or heading for different stops, or minutes apart at the same stop.
        assertEquals(2, distinctVehicles(listOf(vehicle("a"), vehicle("b", lat = 52.5500))).size)
        assertEquals(2, distinctVehicles(listOf(vehicle("a"), vehicle("b", next = "S Westhafen"))).size)
        assertEquals(2, distinctVehicles(listOf(vehicle("a"), vehicle("b", nextInSeconds = 360))).size)
    }

    @Test
    fun withoutANextStopOnlyVeryCloseVehiclesOfOneDirectionAreOne() {
        assertTrue(sameVehicle(vehicle("a", next = null), vehicle("b", next = null, lat = 52.5452)))
        assertFalse(sameVehicle(vehicle("a", next = null), vehicle("b", next = null, lat = 52.5460)))
        assertFalse(sameVehicle(vehicle("a", next = null), vehicle("b", next = null, direction = "S Ostkreuz")))
    }

    @Test
    fun theOrderStaysAndNothingChangesWithoutTwins() {
        val list = listOf(vehicle("a"), vehicle("x", line = "U8"), vehicle("b", realtime = true), vehicle("y", line = "M10"))
        assertEquals(listOf("b", "x", "y"), distinctVehicles(list).map { it.tripId })
        val plain = listOf(vehicle("a"), vehicle("x", line = "U8"))
        assertTrue(distinctVehicles(plain) === plain)
    }
}
