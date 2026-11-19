package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** The vehicles kept in memory for the map (#31): reach, expiry and the least recently used going first. */
class VehicleSnapshotsTest {
    // Four places in a city, a few kilometres apart (made-up spots, no stations).
    private val a = 52.5000 to 13.4000
    private val b = 52.5400 to 13.4000
    private val c = 52.5000 to 13.4800
    private val d = 52.4600 to 13.4000

    private fun cache() = VehicleSnapshots<String>(capacity = 3, maxAgeMillis = 120_000L, reachMeters = 1_500.0)

    @Test
    fun findsTheSamePlaceAndTier() {
        val cache = cache()
        cache.put(a.first, a.second, tier = 2, items = listOf("x", "y"), since = 1_000L, now = 1_000L)
        val hit = cache.find(a.first + 0.002, a.second, tier = 2, now = 5_000L)
        assertNotNull(hit)
        assertEquals(listOf("x", "y"), hit!!.items)
        assertEquals(1_000L, hit.since)
        // Another tier or far away: nothing.
        assertNull(cache.find(a.first, a.second, tier = 1, now = 5_000L))
        assertNull(cache.find(b.first, b.second, tier = 2, now = 5_000L))
    }

    @Test
    fun expiresAfterTheLimit() {
        val cache = cache()
        cache.put(a.first, a.second, 2, listOf("x"), since = 0L, now = 0L)
        assertNotNull(cache.find(a.first, a.second, 2, now = 120_000L))
        assertNull(cache.find(a.first, a.second, 2, now = 120_001L))
        assertEquals(0, cache.size)
    }

    @Test
    fun keepsAtMostThreeLeastRecentlyUsedGoesFirst() {
        val cache = cache()
        cache.put(a.first, a.second, 2, listOf("a"), since = 0L, now = 0L)
        cache.put(b.first, b.second, 2, listOf("b"), since = 0L, now = 0L)
        cache.put(c.first, c.second, 2, listOf("c"), since = 0L, now = 0L)
        // A is used again: B is now the oldest in use and goes with the fourth.
        assertNotNull(cache.find(a.first, a.second, 2, now = 1L))
        cache.put(d.first, d.second, 2, listOf("d"), since = 0L, now = 2L)
        assertEquals(3, cache.size)
        assertNull(cache.find(b.first, b.second, 2, now = 3L))
        assertNotNull(cache.find(a.first, a.second, 2, now = 3L))
        assertNotNull(cache.find(c.first, c.second, 2, now = 3L))
        assertNotNull(cache.find(d.first, d.second, 2, now = 3L))
    }

    @Test
    fun theSamePlaceIsReplacedNotAdded() {
        val cache = cache()
        val first = listOf("old")
        val second = listOf("new")
        cache.put(a.first, a.second, 2, first, since = 0L, now = 0L)
        cache.put(a.first + 0.001, a.second, 2, second, since = 10L, now = 10L)
        assertEquals(1, cache.size)
        assertSame(second, cache.find(a.first, a.second, 2, now = 20L)!!.items)
    }

    @Test
    fun emptyListsAreNotKeptAndClearDropsAll() {
        val cache = cache()
        cache.put(a.first, a.second, 2, emptyList(), since = 0L, now = 0L)
        assertEquals(0, cache.size)
        cache.put(a.first, a.second, 2, listOf("x"), since = 0L, now = 0L)
        cache.clear()
        assertNull(cache.find(a.first, a.second, 2, now = 1L))
    }

    @Test
    fun distanceIsPlausible() {
        // 0.01 degrees north is about 1.1 km.
        val meters = VehicleSnapshots.metersBetween(52.5, 13.4, 52.51, 13.4)
        assertEquals(1113.0, meters, 5.0)
    }
}
