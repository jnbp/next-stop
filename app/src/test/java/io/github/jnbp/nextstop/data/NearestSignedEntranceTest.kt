package io.github.jnbp.nextstop.data

import kotlin.math.cos
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A synthetic station: a U-Bahn under a street, its platform not mapped, two entrances 200 m apart. */
class NearestSignedEntranceTest {
    private class Door(val name: String, val position: Pair<Double, Double>, val marker: String?)

    private fun meters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double =
        hypot((b.first - a.first) * 111_320.0, (b.second - a.second) * 111_320.0 * cos(Math.toRadians(a.first)))

    private val busStop = 52.50000 to 13.40000
    // The entrance across the street from the bus stop, and the one at the other end of the line.
    private val near = Door("near", 52.50020 to 13.40020, "U")
    private val far = Door("far", 52.50180 to 13.40020, "U")
    // The provider's point of the station, the start of the ride's way: at the far end.
    private val providerPoint = 52.50190 to 13.40010
    private val sBahn = Door("s", 52.50010 to 13.40005, "S")

    @Test
    fun theWayInIsTheLineEntranceNearestToTheBusStop() {
        val doors = listOf(far, sBahn, near)
        val chosen = nearestSignedEntrance(doors, "U", { it.marker }) { meters(busStop, it.position) }
        assertEquals("near", chosen?.name)
        // What the old weighing (outside + to the point inside) took: the far one, along the platform.
        val old = doors.filter { it.marker == "U" }.minByOrNull { meters(busStop, it.position) + meters(it.position, providerPoint) }
        assertEquals("far", old?.name)
    }

    @Test
    fun entrancesOfAnotherSignAreNotTaken() {
        val chosen = nearestSignedEntrance(listOf(sBahn, far), "U", { it.marker }) { meters(busStop, it.position) }
        assertEquals("far", chosen?.name)
    }

    @Test
    fun noSignOrNoMarkedEntranceLeavesTheChoiceAsBefore() {
        assertNull(nearestSignedEntrance(listOf(near, far), null, { it.marker }) { meters(busStop, it.position) })
        assertNull(nearestSignedEntrance(listOf(sBahn), "U", { it.marker }) { meters(busStop, it.position) })
    }
}
