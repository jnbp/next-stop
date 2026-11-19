package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which ways of long-distance trains the map far out keeps, and under which key and rank. */
class LongDistanceRulesTest {
    private val a = 52.525 to 13.369
    private val b = 51.040 to 13.732

    @Test
    fun highSpeedTrainsAreMainAxes() {
        assertEquals(LONG_DISTANCE_MAIN, longDistanceRank("HIGHSPEED_RAIL", null))
        assertEquals(LONG_DISTANCE_MAIN, longDistanceRank("LONG_DISTANCE", "ICE 1601"))
        assertEquals(LONG_DISTANCE_OTHER, longDistanceRank("LONG_DISTANCE", "IC 2432"))
        assertEquals(LONG_DISTANCE_OTHER, longDistanceRank("NIGHT_RAIL", "NJ 40"))
        assertEquals(LONG_DISTANCE_MAIN, longDistanceTrackRank("ICE 603"))
        assertEquals(LONG_DISTANCE_OTHER, longDistanceTrackRank("EC 175"))
        assertNull(longDistanceTrackRank("RE 5"))
        assertNull(longDistanceTrackRank(null))
    }

    @Test
    fun oneKeyPerHopInEitherDirection() {
        assertEquals(longDistanceKey(0, "h", listOf(a, b)), longDistanceKey(0, "h", listOf(b, a)))
        assertEquals(LONG_DISTANCE_MAIN, longDistanceKeyRank(longDistanceKey(0, "h", listOf(a, b))))
        assertEquals(LONG_DISTANCE_OTHER, longDistanceKeyRank(longDistanceKey(1, "h", listOf(a, b))))
    }

    @Test
    fun theSameHopOfManyTrainsIsOneLineWithTheFinestWay() {
        val middle = 51.8 to 13.5
        val lines = longDistanceLines(
            listOf(
                LongDistanceHop("HIGHSPEED_RAIL", "ICE 1", listOf(a, b)),
                LongDistanceHop("HIGHSPEED_RAIL", "ICE 2", listOf(b, middle, a)),
                LongDistanceHop("LONG_DISTANCE", "IC 3", listOf(a, b)),
                // A hop that does not move is no way.
                LongDistanceHop("LONG_DISTANCE", "IC 4", listOf(a, a)),
            ),
        )
        assertEquals(2, lines.size)
        assertEquals(3, lines.first { longDistanceKeyRank(it.line) == LONG_DISTANCE_MAIN }.points.size)
    }

    @Test
    fun aWayUnderBothRanksIsDrawnOnceAsMainAxis() {
        val lines = listOf(
            NetworkLine(longDistanceKey(1, "h", listOf(a, b)), listOf(a, b)),
            NetworkLine(longDistanceKey(0, "h", listOf(a, b)), listOf(a, b)),
            NetworkLine(longDistanceKey(1, "h", listOf(a, 50.0 to 12.0)), listOf(a, 50.0 to 12.0)),
        )
        val shown = longDistanceShown(lines)
        assertEquals(2, shown.size)
        assertEquals(1, shown.count { longDistanceKeyRank(it.line) == LONG_DISTANCE_MAIN })
    }

    /** A kept track drawn from station to station (#280, a foreign feed's copy) is not drawn; a real one is. */
    @Test
    fun aKeptAirLineTrackIsNotDrawn() {
        val berlin = 52.5251 to 13.3694
        val spandau = 52.5345 to 13.1968
        val wittenberge = 52.9994 to 11.7576
        val air = listOf(berlin, spandau, wittenberge)
        // A real track, simplified: steps of a few kilometres.
        val real = (0..40).map { step -> (52.5345 + 0.0116 * step) to (13.1968 - 0.0360 * step) }
        val lines = listOf(
            NetworkLine(longDistanceKey(1, "t", air), air),
            NetworkLine(longDistanceKey(1, "t", real), real),
            NetworkLine(longDistanceKey(1, "h", air), air),
        )
        assertEquals(listOf(lines[1], lines[2]), longDistanceShown(lines))
    }
}
