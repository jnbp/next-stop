package io.github.jnbp.nextstop.data

import org.junit.Assert.assertTrue
import org.junit.Test

/** The lines of the rapid network keep their curves round: no visible corners where the rails bend. */
class NetworkShapeTest {
    /** A curve of [radius] metres through [degrees], one point per degree, around a centre in the city. */
    private fun curve(radius: Double, degrees: Int): List<Pair<Double, Double>> = (0..degrees).map { step ->
        val angle = Math.toRadians(step.toDouble())
        val north = radius * Math.sin(angle)
        val east = radius * (1 - Math.cos(angle))
        (52.5 + north / 111_320.0) to (13.4 + east / (111_320.0 * Math.cos(Math.toRadians(52.5))))
    }

    /** The largest change of direction between two segments of [points], in degrees. */
    private fun sharpestTurn(points: List<Pair<Double, Double>>): Double {
        val k = Math.cos(Math.toRadians(points[0].first))
        val headings = points.zipWithNext { a, b -> Math.atan2(b.first - a.first, (b.second - a.second) * k) }
        return headings.zipWithNext { a, b ->
            var d = Math.toDegrees(b - a)
            while (d > 180) d -= 360
            while (d < -180) d += 360
            Math.abs(d)
        }.maxOrNull() ?: 0.0
    }

    @Test
    fun aTightCurveOfTheRailsStaysRound() {
        // A curve of 300 m radius, as many of the U-Bahn: no corner sharper than 20 degrees.
        val simple = simplify(curve(300.0, 90), NETWORK_SIMPLIFY_METRES)
        assertTrue("turn of ${sharpestTurn(simple)} degrees", sharpestTurn(simple) <= 20.0)
    }

    @Test
    fun aStraightStretchStaysTwoPoints() {
        val straight = (0..100).map { (52.5 + it * 0.0001) to 13.4 }
        assertTrue(simplify(straight, NETWORK_SIMPLIFY_METRES).size == 2)
    }
}
