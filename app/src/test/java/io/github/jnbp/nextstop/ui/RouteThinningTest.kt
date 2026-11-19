package io.github.jnbp.nextstop.ui

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ways of a followed trip are worked out again every two seconds (#71, #72): the long way of
 * an ICE ride is thinned once, then comes from the memo; the result is the same as before.
 */
class RouteThinningTest {
    private fun longWay(points: Int): List<Pair<Double, Double>> = (0 until points).map { i ->
        val t = i.toDouble() / (points - 1)
        val bend = 0.05 * sin(t * PI * 9) + 0.002 * sin(t * PI * 400)
        (50.94 + 1.58 * t + bend) to (6.96 + 6.41 * t + bend / 2)
    }

    @Test
    fun aLongWayIsThinnedOnceAndThenRemembered() {
        val way = longWay(30_000)
        val threads = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val id = Thread.currentThread().id
        var started = System.nanoTime()
        var before = threads.getThreadAllocatedBytes(id)
        val first = thinnedRoute(way)
        val firstMillis = (System.nanoTime() - started) / 1_000_000
        val firstBytes = threads.getThreadAllocatedBytes(id) - before
        started = System.nanoTime()
        before = threads.getThreadAllocatedBytes(id)
        val again = thinnedRoute(way)
        val againMicros = (System.nanoTime() - started) / 1_000
        val againBytes = threads.getThreadAllocatedBytes(id) - before
        println("30 000 points: first ${first.size} points in $firstMillis ms, ${firstBytes / 1000} kB; again in $againMicros us, $againBytes B")
        assertSame(first, again)
        assertTrue(first.size <= MAX_ROUTE_POINTS)
        assertTrue("again allocated $againBytes bytes", againBytes < 10_000)
    }

    @Test
    fun anEqualButOtherWayIsThinnedTheSame() {
        val way = longWay(5_000)
        assertEquals(thinnedRoute(way), thinnedRoute(ArrayList(way)))
    }

    @Test
    fun aShortWayStaysAsItIs() {
        val way = listOf(52.0 to 13.0, 52.1 to 13.1)
        assertSame(way, thinnedRoute(way))
    }
}
