package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The vehicles kept in memory work across zoom levels (#74, see VehicleSnapshots.within and vehiclesForView). */
class VehicleViewCacheTest {
    // Made-up spots in a city, no stations.
    private fun vehicle(id: String, latitude: Double, longitude: Double) =
        Vehicle(id, "X$id", "bus", "Somewhere", listOf(latitude to longitude), null, null)

    private val cityTier = MapTier.City.ordinal
    private val closeTier = MapTier.Neighbourhood.ordinal

    // The wide view: the whole city. The close view: a small part of it.
    private val wide = doubleArrayOf(52.70, 52.30, 13.80, 13.00)
    private val close = doubleArrayOf(52.51, 52.49, 13.42, 13.38)

    private fun cache() = VehicleSnapshots<Vehicle>(capacity = 6, maxAgeMillis = 120_000L, reachMeters = 1_500.0)

    private fun VehicleSnapshots<Vehicle>.inBox(box: DoubleArray, now: Long) =
        within(box[0], box[1], box[2], box[3], now, { it.path.lastOrNull() }, { it.tripId })

    private val far = List(40) { vehicle("far$it", 52.35 + it * 0.008, 13.05 + it * 0.005) }
    private val near = listOf(vehicle("n1", 52.50, 13.40), vehicle("n2", 52.505, 13.41))

    @Test
    fun zoomingOutAgainShowsTheWideViewAtOnce() {
        val cache = cache()
        // Zoomed out: many vehicles. Zoomed in: the few close ones replace them.
        cache.put(52.5, 13.4, cityTier, far + near, since = 1_000L, now = 1_000L)
        cache.put(52.5, 13.4, closeTier, near, since = 20_000L, now = 20_000L)
        val seen = HashMap<String, Long>()
        val shown = vehiclesForView(near, cache.inBox(wide, 30_000L), seen, wide[0], wide[1], wide[2], wide[3])
        assertEquals(far.size + near.size, shown.size)
        assertEquals(far.size + near.size, shown.map { it.tripId }.toSet().size)
        // The added ones are known as seen when they were loaded, so the next answer keeps them a while.
        assertEquals(1_000L, seen["far0"])
        assertTrue("n1" !in seen)
    }

    @Test
    fun zoomingInKeepsOnlyThoseInView() {
        val cache = cache()
        cache.put(52.5, 13.4, cityTier, far + near, since = 1_000L, now = 1_000L)
        val shown = vehiclesForView(far + near, cache.inBox(close, 2_000L), HashMap(), close[0], close[1], close[2], close[3])
        assertEquals(setOf("n1", "n2"), shown.map { it.tripId }.toSet())
    }

    @Test
    fun nothingNewGivesTheSameList() {
        val cache = cache()
        cache.put(52.5, 13.4, closeTier, near, since = 1_000L, now = 1_000L)
        val shown = vehiclesForView(near, cache.inBox(close, 2_000L), HashMap(), close[0], close[1], close[2], close[3])
        assertSame(near, shown)
    }

    @Test
    fun theNewestSnapshotWinsForAVehicleKnownTwice() {
        val cache = cache()
        val old = vehicle("v", 52.40, 13.20)
        val moved = vehicle("v", 52.41, 13.21)
        cache.put(52.4, 13.2, cityTier, listOf(old), since = 1_000L, now = 1_000L)
        cache.put(52.4, 13.2, closeTier, listOf(moved), since = 5_000L, now = 5_000L)
        val found = cache.inBox(wide, 6_000L)
        assertEquals(1, found.size)
        assertSame(moved, found[0].first)
        assertEquals(5_000L, found[0].second)
    }

    @Test
    fun expiredSnapshotsGiveNothing() {
        val cache = cache()
        cache.put(52.5, 13.4, cityTier, far, since = 0L, now = 0L)
        assertEquals(0, cache.inBox(wide, 120_001L).size)
    }

    @Test
    fun aVehicleShownIsNotAddedAgainFromMemory() {
        val cache = cache()
        cache.put(52.5, 13.4, cityTier, near, since = 1_000L, now = 1_000L)
        val seen = hashMapOf("n1" to 9_000L)
        val shown = vehiclesForView(listOf(near[0]), cache.inBox(wide, 10_000L), seen, wide[0], wide[1], wide[2], wide[3])
        assertEquals(listOf("n1", "n2"), shown.map { it.tripId })
        // A later sighting is not set back to the older snapshot.
        assertEquals(9_000L, seen["n1"])
    }
}
