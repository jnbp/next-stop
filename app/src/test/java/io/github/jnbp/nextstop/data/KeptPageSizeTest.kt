package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The kept page of a long-distance search (#72): every connection of the page with ways of
 * tens of thousands of points (an ICE across the country as Transitous sends it) was written
 * whole, as a tree of boxed numbers and then one long text, on each write. That ran the heap out.
 * The kept ways are thinned, so the page stays small whatever the provider sends.
 */
class KeptPageSizeTest {
    private val start = OffsetDateTime.parse("2026-11-18T08:00:00+01:00")

    // A winding way of [points] points from one city to another, about 20 m apart, like the rails of a long run.
    private fun longWay(points: Int, seed: Double): List<Pair<Double, Double>> {
        val from = 50.94 to 6.96
        val to = 52.52 to 13.37
        return (0 until points).map { i ->
            val t = i.toDouble() / (points - 1)
            val bend = 0.05 * sin(t * PI * 9 + seed) + 0.002 * sin(t * PI * 400)
            (from.first + (to.first - from.first) * t + bend) to (from.second + (to.second - from.second) * t + bend / 2)
        }
    }

    private fun iceLeg(index: Int, points: Int) = Leg(
        line = "ICE ${600 + index}", product = "express", walking = false,
        from = "Origin Hbf", to = "Destination Hbf",
        departure = start.plusMinutes(index * 30L), plannedDeparture = start.plusMinutes(index * 30L),
        arrival = start.plusMinutes(index * 30L + 270), plannedArrival = start.plusMinutes(index * 30L + 270),
        departurePlatform = "4", arrivalPlatform = "12", direction = "Destination", cancelled = false, realtime = true,
        path = longWay(points, index.toDouble()), tripId = "trip-$index",
        stopovers = (1..12).map { Stopover("Stop $it", start.plusMinutes(it * 20L), start.plusMinutes(it * 20L + 2)) },
    )

    private fun bigPage(points: Int): KeptPage {
        val journeys = (0 until 17).map { Journey(legs = listOf(iceLeg(it, points / 2), iceLeg(it + 100, points / 2))) }
        return KeptPage(
            savedAt = 1L, tab = "Route", tripOpen = true, followed = journeys[3], origin = null, destination = null,
            via = null, time = null, timeArrival = false, journeys = journeys, selectedId = journeys[3].id,
            detailPage = true, planning = false,
        )
    }

    private fun meters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val north = (b.first - a.first) * 111_320.0
        val east = (b.second - a.second) * 111_320.0 * cos((a.first + b.first) / 2 * PI / 180)
        return sqrt(north * north + east * east)
    }

    // The distance of [point] to the nearest segment of [way], in metres (flat, fine for short segments).
    private fun distanceToWay(point: Pair<Double, Double>, way: List<Pair<Double, Double>>): Double {
        var best = Double.MAX_VALUE
        val scale = cos(point.first * PI / 180)
        for (i in 1 until way.size) {
            val ax = way[i - 1].second * scale; val ay = way[i - 1].first
            val bx = way[i].second * scale; val by = way[i].first
            val px = point.second * scale; val py = point.first
            val dx = bx - ax; val dy = by - ay
            val len = dx * dx + dy * dy
            val t = if (len <= 0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len).coerceIn(0.0, 1.0)
            val ex = px - (ax + dx * t); val ey = py - (ay + dy * t)
            best = minOf(best, sqrt(ex * ex + ey * ey) * 111_320.0)
        }
        return best
    }

    @Test
    fun aLongDistancePageStaysSmall() {
        // 17 connections, 60 000 points each (two ICE rides of 30 000): about a million points in all.
        val page = bigPage(60_000)
        val threads = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val before = threads.getThreadAllocatedBytes(Thread.currentThread().id)
        val text = keptPageJson(page)
        val allocated = threads.getThreadAllocatedBytes(Thread.currentThread().id) - before
        println("kept page of ${17 * 60_000} points: ${text.length} characters, ${allocated / 1_000_000} MB allocated to write it")
        // Was well over 30 million characters (and a tree of two million boxed numbers before that).
        assertTrue("kept page has ${text.length} characters", text.length < 1_500_000)
    }

    @Test
    fun aThinnedWayKeepsItsEndsAndShape() {
        val way = longWay(30_000, 1.0)
        val kept = keptPath(way)
        assertTrue("kept ${kept.size} points", kept.size <= KEPT_PATH_MAX_POINTS)
        assertTrue(meters(kept.first(), way.first()) < 1.5)
        assertTrue(meters(kept.last(), way.last()) < 1.5)
        // Every point of the way lies near the kept line (it follows the bends, not a straight cut).
        val worst = way.filterIndexed { i, _ -> i % 97 == 0 }.maxOf { distanceToWay(it, kept) }
        println("thinned to ${kept.size} points, worst deviation ${"%.1f".format(worst)} m")
        assertTrue("deviation $worst m", worst < 250.0)
    }

    @Test
    fun aShortWayComesBackAsItWas() {
        val way = listOf(52.521 to 13.411, 52.5205 to 13.4101, 52.509 to 13.376)
        assertEquals(way, keptPath(way))
    }

    @Test
    fun aRestoredLongPageStillReadsBack() {
        val page = bigPage(4_000)
        val back = keptPageOf(keptPageJson(page))!!
        // At most 16 connections are kept, the picked one among them.
        assertEquals(page.journeys!!.take(16).map { it.id }, back.journeys!!.map { it.id })
        assertEquals(page.followed!!.id, back.followed!!.id)
    }
}
