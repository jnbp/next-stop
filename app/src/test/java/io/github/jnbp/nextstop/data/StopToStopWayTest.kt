package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A replacement bus without a shape is not driven across the fields. */
class StopToStopWayTest {
    // Three stops of a replacement bus a few kilometres apart in the countryside.
    private val stops = listOf(52.4000 to 13.0000, 52.4300 to 13.0400, 52.4500 to 13.1000)

    @Test
    fun polylineThroughTheStopsIsNoWay() {
        // As HAFAS draws a trip without a shape: the stops' coordinates, a little rounded.
        val straight = listOf(52.40001 to 13.00002, 52.43002 to 13.04001, 52.45 to 13.1)
        assertTrue(isStopToStopWay(straight, stops))
        assertTrue(isStopToStopWay(emptyList(), stops))
        assertTrue(movesWithoutWay("bus", straight, stops))
        // A train on such a line keeps moving: rails run nearly straight.
        assertFalse(movesWithoutWay("regional", straight, stops))
    }

    @Test
    fun roadWayIsAWay() {
        val road = listOf(
            52.4000 to 13.0000, 52.4100 to 13.0050, 52.4200 to 13.0250, 52.4300 to 13.0400,
            52.4350 to 13.0700, 52.4500 to 13.1000,
        )
        assertFalse(isStopToStopWay(road, stops))
        assertFalse(movesWithoutWay("bus", road, stops))
    }

    @Test
    fun straightHopOfABus() {
        assertTrue(isStraightRoadHop("bus", listOf(stops[0], stops[1])))
        // A short hop may be straight: a street.
        assertFalse(isStraightRoadHop("bus", listOf(52.4000 to 13.0000, 52.4020 to 13.0000)))
        assertFalse(isStraightRoadHop("tram", listOf(stops[0], stops[1])))
        assertFalse(isStraightRoadHop("bus", listOf(stops[0], 52.41 to 13.03, stops[1])))
    }

    @Test
    fun vehicleWithoutAWayStandsAtAStop() {
        // While it stands at a stop, there.
        assertEquals(0, parkedStop(0f, 3))
        assertEquals(1, parkedStop(1f, 3))
        // Once it has left, at the stop it is heading to, from the first moment on.
        assertEquals(1, parkedStop(0.05f, 3))
        assertEquals(1, parkedStop(0.6f, 3))
        assertEquals(2, parkedStop(1.4f, 3))
        assertEquals(2, parkedStop(5f, 3))
        assertEquals(0, parkedStop(-1f, 3))
    }

    @Test
    fun vehicleWithoutAWayIsAtTheStationItHeadsTo() {
        val wittenberg = 51.8676 to 12.6624
        val bitterfeld = 51.6230 to 12.3168
        // Standing at Wittenberg until it leaves at 1000.
        assertEquals(wittenberg, parkedPoint(wittenberg, 1000L, bitterfeld, 900L))
        // Left: at Bitterfeld, where it is heading, also long before it gets there.
        assertEquals(bitterfeld, parkedPoint(wittenberg, 1000L, bitterfeld, 1001L))
        assertEquals(bitterfeld, parkedPoint(wittenberg, null, bitterfeld, 900L))
        // Only one of them known: there.
        assertEquals(wittenberg, parkedPoint(wittenberg, 1000L, null, 2000L))
        assertEquals(bitterfeld, parkedPoint(null, null, bitterfeld, 2000L))
    }
}
