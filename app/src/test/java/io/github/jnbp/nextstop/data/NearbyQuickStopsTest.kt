package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The quick picks of "Von wo?": nearest first, each only for a line no nearer pick has (#173). Made-up stops only. */
class NearbyQuickStopsTest {
    private fun stop(id: String) = Stop("90000000$id", "Stop $id", 52.0, 13.0, null)
    private fun line(name: String, product: String = "bus") = StopLine(name, product)
    private fun candidate(id: String, meters: Int, vararg lines: StopLine) = QuickStopCandidate(stop(id), meters, lines.toList())
    private fun ids(picks: List<QuickStop>) = picks.map { it.stop.id }

    @Test
    fun aFartherStopOfTheSameLineIsHidden() {
        val a = candidate("1", 100, line("1"))
        val b = candidate("2", 200, line("1"))
        val c = candidate("3", 300, line("2"))
        assertEquals(listOf(a.stop.id, c.stop.id), ids(nearbyQuickStops(listOf(b, c, a))))
    }

    @Test
    fun theLinesThatMakeItWorthShowing() {
        val a = candidate("1", 100, line("1"))
        val b = candidate("2", 200, line("1"), line("2"), line("2"))
        val picks = nearbyQuickStops(listOf(a, b))
        assertEquals(listOf(line("2")), picks[1].newLines)
    }

    @Test
    fun atMostFour() {
        val many = (1..6).map { candidate("$it", it * 50, line("L$it")) }
        assertEquals(4, nearbyQuickStops(many).size)
    }

    @Test
    fun onlyWithinTheWalk() {
        // 800 m on foot: 640 m as the crow flies (x 1.25) is in, 700 m (875 m on foot) is out.
        val near = candidate("1", 640, line("1"))
        val far = candidate("2", 700, line("2"))
        assertEquals(listOf(near.stop.id), ids(nearbyQuickStops(listOf(near, far))))
    }

    @Test
    fun theSameNameOfAnotherProductIsAnotherLine() {
        val a = candidate("1", 100, line("1", "bus"))
        val b = candidate("2", 200, line("1", "tram"))
        assertEquals(2, nearbyQuickStops(listOf(a, b)).size)
    }

    @Test
    fun walkMinutesComeFromTheMetres() {
        // 240 m as the crow flies are 300 m on foot.
        val picks = nearbyQuickStops(listOf(candidate("1", 240, line("1"))), minutesOf = { it / 100L })
        assertEquals(3L, picks.single().walkMinutes)
    }
}
