package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A refreshed trip's way with the same points counts as the same way; any other point does not. */
class SameWayTest {
    private val way = listOf(52.52 to 13.41, 52.53 to 13.42, 52.54 to 13.44)

    @Test
    fun sameListOrSamePoints() {
        assertTrue(sameWay(way, way))
        assertTrue(sameWay(way, ArrayList(way)))
        assertTrue(sameWay(way, way.map { (it.first + 0.0) to (it.second + 0.0) }))
        assertTrue(sameWay(null, null))
    }

    @Test
    fun anotherWay() {
        assertFalse(sameWay(way, way.dropLast(1)))
        assertFalse(sameWay(way, way.take(2) + (52.54 to 13.4400001)))
        assertFalse(sameWay(way, null))
    }
}
