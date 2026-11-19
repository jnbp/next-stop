package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every walk with a point to go to offers Google Maps, also one of 0 minutes (#131). Made-up stops only. */
class WalkNavigationTest {
    private val at: OffsetDateTime = OffsetDateTime.parse("2026-01-01T10:00:00+01:00")
    private fun stop(id: String, lat: Double) = Stop("90000000$id", "Stop $id", lat, 13.0, null)
    private fun leg(from: Stop, to: Stop, walking: Boolean, minutes: Long = 0) = Leg(
        if (walking) null else "M1", if (walking) "walking" else "bus", walking, from.name, to.name,
        at, at, at.plusMinutes(minutes), at.plusMinutes(minutes), null, null, null, false, fromPlace = from, toPlace = to,
    )

    private val a = stop("1", 52.0)
    private val b = stop("2", 52.001)
    private val c = stop("3", 52.002)

    @Test
    fun aWalkOfNoMinutesStillLeadsToTheNextRide() {
        val walk = leg(a, b, walking = true, minutes = 0)
        val next = leg(c, a, walking = false, minutes = 5)
        val target = walkNavigationTarget(walk, next)
        assertEquals(c.position, target)
        assertTrue(offersWalkNavigation(target, over = false))
    }

    @Test
    fun withoutARideAfterItTheEndOfTheWalk() {
        assertEquals(b.position, walkNavigationTarget(leg(a, b, walking = true, minutes = 3)))
    }

    @Test
    fun noPointOrOverOffersNothing() {
        assertFalse(offersWalkNavigation(null, over = false))
        assertFalse(offersWalkNavigation(b.position, over = true))
    }
}
