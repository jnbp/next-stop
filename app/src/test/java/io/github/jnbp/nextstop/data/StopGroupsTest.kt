package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Favourites of several stops (see StopGroups.kt) and the keys of departure rows. */
class StopGroupsTest {
    private fun stop(id: String, distance: Int? = null) = Stop(id, "Stop $id", 52.5, 13.3, distance)

    @Test
    fun theSameStopsInAnyOrderAreTheSameFavourite() {
        val group = stopGroupOf(listOf(stop("a"), stop("b"), stop("c")), "Mine")!!
        assertTrue(sameStops(group, listOf(stop("c"), stop("a"), stop("b"))))
        assertTrue(sameStops(group, listOf(stop("b"), stop("a"), stop("c"), stop("a"))))
        assertEquals(false, sameStops(group, listOf(stop("a"), stop("b"))))
        assertEquals(false, sameStops(group, listOf(stop("a"), stop("b"), stop("c"), stop("d"))))
        assertEquals(group, findStopGroup(listOf(group), listOf(stop("b"), stop("c"), stop("a"))))
    }

    @Test
    fun oneStopIsNoFavouriteOfSeveral() {
        assertNull(stopGroupOf(listOf(stop("a"))))
        assertNull(stopGroupOf(listOf(stop("a"), stop("a"))))
        // A group that holds a single stop is never the favourite of that one stop.
        assertNull(findStopGroup(listOf(StopGroup(listOf(stop("a")))), listOf(stop("a"))))
    }

    @Test
    fun aGroupKeepsTheOrderOfItsStopsWithoutDistances() {
        val group = stopGroupOf(listOf(stop("b", 120), stop("a", 300), stop("b", 120)), "  Home stops ")!!
        assertEquals(listOf("b", "a"), group.stops.map { it.id })
        assertEquals(listOf<Int?>(null, null), group.stops.map { it.distanceMeters })
        assertEquals("Home stops", group.name)
    }

    @Test
    fun savingTheSameStopsAgainReplacesTheOldFavourite() {
        val first = stopGroupOf(listOf(stop("a"), stop("b")), "Old")!!
        val other = stopGroupOf(listOf(stop("c"), stop("d")))!!
        val again = stopGroupOf(listOf(stop("b"), stop("a")), "New")!!
        val groups = withStopGroup(withStopGroup(listOf(first), other), again)
        assertEquals(listOf("", "New"), groups.map { it.name })
    }

    @Test
    fun aRemovedFavouriteComesBackInItsPlace() {
        val a = stopGroupOf(listOf(stop("a"), stop("b")))!!
        val b = stopGroupOf(listOf(stop("c"), stop("d")))!!
        val c = stopGroupOf(listOf(stop("e"), stop("f")))!!
        val groups = listOf(a, b, c)
        val index = groups.indexOfFirst { sameStops(it, listOf(stop("d"), stop("c"))) }
        val removed = withoutStopGroup(groups, listOf(stop("d"), stop("c")))
        assertEquals(listOf(a, c), removed)
        assertEquals(groups, restoreStopGroup(removed, b, index))
        // Undone twice, it is still there once.
        assertEquals(groups, restoreStopGroup(groups, b, index))
    }

    @Test
    fun storedFavouritesComeBackAsTheyWereSaved() {
        val saved = listOf(
            stopGroupOf(listOf(stop("a"), stop("b"), stop("c")), "Three")!!,
            stopGroupOf(listOf(stop("x"), stop("y")))!!,
        )
        assertEquals(saved, storedStopGroups(saved))
        // What could not be read whole is left out; a set of stops stored twice counts once.
        val broken = saved + StopGroup(listOf(stop("z"))) + StopGroup(listOf(stop("c"), stop("b"), stop("a")), "Twice")
        assertEquals(saved, storedStopGroups(broken))
    }

    private val at = OffsetDateTime.parse("2026-11-14T10:00:00+01:00")

    private fun departure(line: String, minutes: Long, trip: String? = null, delay: Long = 0, stop: Stop? = null) = Departure(
        line = line, product = "bus", direction = "Somewhere",
        time = at.plusMinutes(minutes + delay), plannedTime = at.plusMinutes(minutes),
        delaySeconds = (delay * 60).toInt(), platform = null, cancelled = false, tripId = trip, stop = stop,
    )

    @Test
    fun rowKeysOutlastARefresh() {
        val before = listOf(departure("M1", 1, "t1"), departure("100", 3), departure("M1", 6, "t2"))
        // A minute later: the first has left, the second is late, a new one came in at the end.
        val after = listOf(departure("100", 3, delay = 2), departure("M1", 6, "t2"), departure("M1", 12, "t3"))
        val old = departureKeys(before)
        val new = departureKeys(after)
        assertEquals(old[1], new[0])
        assertEquals(old[2], new[1])
        assertTrue(new[2] !in old)
    }

    @Test
    fun rowKeysAreUniqueAndApartByStop() {
        val one = stop("one")
        val two = stop("two")
        val list = listOf(
            departure("100", 3), departure("100", 3),
            departure("M1", 5, "t", stop = one), departure("M1", 5, "t", stop = two),
        )
        val keys = departureKeys(list)
        assertEquals(keys.size, keys.toSet().size)
        assertNotEquals(keys[2], keys[3])
    }
}
